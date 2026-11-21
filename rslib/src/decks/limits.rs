// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::decks::deck::normal::DayLimit;
use id_tree::InsertBehavior;
use id_tree::Node;
use id_tree::NodeId;
use id_tree::Tree;

use super::immediate_parent_name;
use super::Deck;
use super::NormalDeck;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::prelude::*;

#[derive(Debug, Clone, Copy)]
pub(crate) enum LimitKind {
    Review,
    New,
}

/// The deck's review limit for today, or its regular one, if any is
/// configured.
pub fn current_review_limit(deck: &NormalDeck, today: u32) -> Option<u32> {
    review_limit_today(deck, today).or(deck.review_limit)
}

/// The deck's new limit for today, or its regular one, if any is
/// configured.
pub fn current_new_limit(deck: &NormalDeck, today: u32) -> Option<u32> {
    new_limit_today(deck, today).or(deck.new_limit)
}

/// The deck's review limit for today.
pub fn review_limit_today(deck: &NormalDeck, today: u32) -> Option<u32> {
    deck.review_limit_today
        .and_then(|day_limit| limit_if_today(day_limit, today))
}

/// The deck's new limit for today.
pub fn new_limit_today(deck: &NormalDeck, today: u32) -> Option<u32> {
    deck.new_limit_today
        .and_then(|day_limit| limit_if_today(day_limit, today))
}

pub fn limit_if_today(limit: DayLimit, today: u32) -> Option<u32> {
    (limit.today == today).then_some(limit.limit)
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct RemainingLimits {
    pub(crate) review: u32,
    pub(crate) new: u32,
    pub(crate) cap_new_to_review: bool,
}

impl RemainingLimits {
    pub(crate) fn new(
        deck: &Deck,
        config: Option<&DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
    ) -> Self {
        if let Ok(normal) = deck.normal() {
            if let Some(config) = config {
                return Self::new_for_normal_deck(
                    deck,
                    today,
                    new_cards_ignore_review_limit,
                    normal,
                    config,
                );
            }
        }
        Self::default()
    }

    fn new_for_normal_deck(
        deck: &Deck,
        today: u32,
        new_cards_ignore_review_limit: bool,
        normal: &NormalDeck,
        config: &DeckConfig,
    ) -> RemainingLimits {
        Self::new_for_normal_deck_v3(deck, today, new_cards_ignore_review_limit, normal, config)
    }

    fn new_for_normal_deck_v3(
        deck: &Deck,
        today: u32,
        new_cards_ignore_review_limit: bool,
        normal: &NormalDeck,
        config: &DeckConfig,
    ) -> RemainingLimits {
        let mut review_limit =
            current_review_limit(normal, today).unwrap_or(config.inner.reviews_per_day) as i32;
        let mut new_limit =
            current_new_limit(normal, today).unwrap_or(config.inner.new_per_day) as i32;
        let (new_today_count, review_today_count) = deck.new_rev_counts(today);

        review_limit -= review_today_count;
        new_limit -= new_today_count;
        if !new_cards_ignore_review_limit {
            review_limit -= new_today_count;
            new_limit = new_limit.min(review_limit);
        }

        Self {
            review: review_limit.max(0) as u32,
            new: new_limit.max(0) as u32,
            cap_new_to_review: !new_cards_ignore_review_limit,
        }
    }

    pub(crate) fn get(&self, kind: LimitKind) -> u32 {
        match kind {
            LimitKind::Review => self.review,
            LimitKind::New => self.new,
        }
    }

    pub(crate) fn cap_to(&mut self, limits: RemainingLimits) {
        self.review = self.review.min(limits.review);
        self.new = self.new.min(limits.new);
    }

    /// True if some limit was decremented to 0.
    fn decrement(&mut self, kind: LimitKind, count: u32) -> DecrementResult {
        let before = *self;
        match kind {
            LimitKind::Review => {
                self.review = self.review.saturating_sub(count);
                if self.cap_new_to_review {
                    self.new = self.new.min(self.review);
                }
            }
            LimitKind::New => self.new = self.new.saturating_sub(count),
        }
        DecrementResult::new(&before, self)
    }
}

struct DecrementResult {
    count_reached_zero: bool,
}

impl DecrementResult {
    fn new(before: &RemainingLimits, after: &RemainingLimits) -> Self {
        Self {
            count_reached_zero: before.review > 0 && after.review == 0
                || before.new > 0 && after.new == 0,
        }
    }
}

impl Default for RemainingLimits {
    fn default() -> Self {
        RemainingLimits {
            review: 9999,
            new: 9999,
            cap_new_to_review: false,
        }
    }
}

pub(crate) fn remaining_limits_map<'a>(
    decks: impl Iterator<Item = &'a Deck>,
    config: &'a HashMap<DeckConfigId, DeckConfig>,
    today: u32,
    new_cards_ignore_review_limit: bool,
) -> HashMap<DeckId, RemainingLimits> {
    decks
        .map(|deck| {
            (
                deck.id,
                RemainingLimits::new(
                    deck,
                    deck.config_id().and_then(|id| config.get(&id)),
                    today,
                    new_cards_ignore_review_limit,
                ),
            )
        })
        .collect()
}

/// Wrapper of [RemainingLimits] with some additional meta data.
#[derive(Debug, Clone, Copy)]
struct NodeLimits {
    limits: RemainingLimits,
    /// Reviews still needed to satisfy this deck's RWKV daily floor.
    rwkv_review_minimum: u32,
    respect_subdeck_minimums: bool,
    depth: usize,
}

impl NodeLimits {
    fn new(
        deck: &Deck,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
        minimum_enabled: bool,
        depth: usize,
    ) -> Self {
        Self {
            limits: RemainingLimits::new(
                deck,
                deck.config_id().and_then(|id| config.get(&id)),
                today,
                new_cards_ignore_review_limit,
            ),
            rwkv_review_minimum: if minimum_enabled {
                remaining_rwkv_review_minimum(
                    deck,
                    deck.config_id().and_then(|id| config.get(&id)),
                    today,
                )
            } else {
                0
            },
            respect_subdeck_minimums: minimum_enabled
                && deck
                    .config_id()
                    .and_then(|id| config.get(&id))
                    .is_some_and(|config| config.inner.rwkv_review_instant_order_enabled)
                && deck.rwkv_respect_subdeck_minimums(),
            depth,
        }
    }
}

fn remaining_rwkv_review_minimum(deck: &Deck, config: Option<&DeckConfig>, today: u32) -> u32 {
    let minimum = config
        .filter(|config| config.inner.rwkv_review_instant_order_enabled)
        .map(|config| config.inner.rwkv_review_minimum_reviews_per_day)
        .unwrap_or_default();
    let (_, reviewed_today) = deck.new_rev_counts(today);
    (i64::from(minimum) - i64::from(reviewed_today)).clamp(0, u32::MAX as i64) as u32
}

#[derive(Debug, Clone)]
pub(crate) struct LimitTreeMap {
    /// A tree representing the remaining limits of the active deck hierarchy.
    //
    // As long as we never (1) allow a tree without a root, (2) remove nodes,
    // and (3) have more than 1 tree, it's safe to unwrap on Tree::get() and
    // Tree::root_node_id(), even if we clone Nodes.
    tree: Tree<NodeLimits>,
    /// A map to access the tree node of a deck.
    map: HashMap<DeckId, NodeId>,
}

impl LimitTreeMap {
    /// [Deck]s must be sorted by name.
    pub(crate) fn build(
        decks: &[Deck],
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
        rwkv_minimum_deck: DeckId,
    ) -> Self {
        let root_limits = NodeLimits::new(
            &decks[0],
            config,
            today,
            new_cards_ignore_review_limit,
            decks[0].id == rwkv_minimum_deck,
            0,
        );
        let mut tree = Tree::new();
        let root_id = tree
            .insert(Node::new(root_limits), InsertBehavior::AsRoot)
            .unwrap();

        let mut map = HashMap::new();
        map.insert(decks[0].id, root_id.clone());

        let mut limits = Self { tree, map };
        let mut node_ids_by_name =
            HashMap::from([(decks[0].name.as_native_str(), root_id.clone())]);
        for deck in &decks[1..] {
            let mut parent_name = immediate_parent_name(deck.name.as_native_str());
            let parent_node_id = loop {
                let Some(name) = parent_name else {
                    break root_id.clone();
                };
                if let Some(node_id) = node_ids_by_name.get(name) {
                    break node_id.clone();
                }
                parent_name = immediate_parent_name(name);
            };
            let minimum_enabled = deck.id == rwkv_minimum_deck
                || limits
                    .tree
                    .get(&parent_node_id)
                    .unwrap()
                    .data()
                    .respect_subdeck_minimums;
            let child_node_id = limits.insert_child_node(
                deck,
                parent_node_id,
                config,
                today,
                new_cards_ignore_review_limit,
                minimum_enabled,
            );
            node_ids_by_name.insert(deck.name.as_native_str(), child_node_id);
        }

        limits
    }

    fn insert_child_node(
        &mut self,
        child_deck: &Deck,
        parent_node_id: NodeId,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
        minimum_enabled: bool,
    ) -> NodeId {
        let depth = self.tree.get(&parent_node_id).unwrap().data().depth + 1;
        let mut child_limits = NodeLimits::new(
            child_deck,
            config,
            today,
            new_cards_ignore_review_limit,
            minimum_enabled,
            depth,
        );
        child_limits
            .limits
            .cap_to(self.get_node_limits(&parent_node_id));
        let child_node_id = self
            .tree
            .insert(
                Node::new(child_limits),
                InsertBehavior::UnderNode(&parent_node_id),
            )
            .unwrap();
        self.map.insert(child_deck.id, child_node_id.clone());
        child_node_id
    }

    fn get_node_id(&self, deck_id: DeckId) -> Result<&NodeId> {
        self.map
            .get(&deck_id)
            .or_invalid("deck not found in limits map")
    }

    fn get_node_limits(&self, node_id: &NodeId) -> RemainingLimits {
        self.tree.get(node_id).unwrap().data().limits
    }

    fn get_deck_limits(&self, deck_id: DeckId) -> Result<RemainingLimits> {
        self.get_node_id(deck_id)
            .map(|node_id| self.get_node_limits(node_id))
    }

    fn get_root_limits(&self) -> RemainingLimits {
        self.get_node_limits(self.tree.root_node_id().unwrap())
    }

    pub(crate) fn root_limit_reached(&self, kind: LimitKind) -> bool {
        self.get_root_limits().get(kind) == 0
    }

    pub(crate) fn remaining_root_limit(&self, kind: LimitKind) -> u32 {
        self.get_root_limits().get(kind)
    }

    pub(crate) fn limit_reached(&self, deck_id: DeckId, kind: LimitKind) -> Result<bool> {
        Ok(self.get_deck_limits(deck_id)?.get(kind) == 0)
    }

    pub(crate) fn decrement_deck_and_parent_limits(
        &mut self,
        deck_id: DeckId,
        kind: LimitKind,
    ) -> Result<()> {
        let node_id = self.get_node_id(deck_id)?.clone();
        self.decrement_node_and_parent_limits(&node_id, kind, 1);
        Ok(())
    }

    pub(crate) fn reserve_review(
        &mut self,
        deck_id: DeckId,
        original_deck_id: DeckId,
    ) -> Result<()> {
        let node_id = self.get_node_id(deck_id)?.clone();
        self.decrement_node_and_parent_limits(&node_id, LimitKind::Review, 1);
        if original_deck_id.0 == 0 {
            self.decrement_rwkv_review_minimum(&node_id, 1);
        }
        Ok(())
    }

    /// Reserve only reviews that fit the daily limits while estimating deck
    /// counts.
    pub(crate) fn reserve_counted_rwkv_reviews(
        &mut self,
        deck_id: DeckId,
        count: u32,
    ) -> Result<u32> {
        let node_id = self.get_node_id(deck_id)?.clone();
        let mut available = count;
        let mut ancestor = Some(node_id.clone());
        while let Some(id) = ancestor {
            let node = self.tree.get(&id).unwrap();
            available = available.min(node.data().limits.review);
            ancestor = node.parent().cloned();
        }
        if available > 0 {
            self.decrement_node_and_parent_limits(&node_id, LimitKind::Review, available);
            self.decrement_rwkv_review_minimum(&node_id, available);
        }
        Ok(available)
    }

    pub(crate) fn reserve_rwkv_reviews_if_present(&mut self, deck_id: DeckId, count: u32) {
        if let Some(node_id) = self.map.get(&deck_id).cloned() {
            self.decrement_rwkv_review_minimum(&node_id, count);
        }
    }

    /// More specific subdeck floors are filled first so they also satisfy
    /// their ancestors before the remaining parent-wide target is filled.
    pub(crate) fn rwkv_review_minimum_depths(&self) -> Vec<usize> {
        let mut depths = self
            .map
            .values()
            .filter_map(|id| self.tree.get(id).ok())
            .filter(|node| node.data().rwkv_review_minimum > 0)
            .map(|node| node.data().depth)
            .collect::<Vec<_>>();
        depths.sort_unstable_by(|a, b| b.cmp(a));
        depths.dedup();
        depths
    }

    pub(crate) fn rwkv_review_minimum_remaining_at_depth(
        &self,
        deck_id: DeckId,
        depth: usize,
    ) -> Result<bool> {
        let mut node_id = Some(self.get_node_id(deck_id)?.clone());
        while let Some(current) = node_id {
            let node = self.tree.get(&current).unwrap();
            if node.data().depth == depth {
                return Ok(node.data().rwkv_review_minimum > 0);
            }
            if node.data().depth < depth {
                break;
            }
            node_id = node.parent().cloned();
        }
        Ok(false)
    }

    pub(crate) fn has_rwkv_review_minimum_at_depth(&self, depth: usize) -> bool {
        self.map.values().any(|id| {
            self.tree
                .get(id)
                .is_ok_and(|node| node.data().depth == depth && node.data().rwkv_review_minimum > 0)
        })
    }

    pub(crate) fn any_rwkv_review_minimum_remaining(&self) -> bool {
        self.map.values().any(|node_id| {
            self.tree
                .get(node_id)
                .is_ok_and(|node| node.data().rwkv_review_minimum > 0)
        })
    }

    pub(crate) fn reserve_new_card(&mut self, deck_id: DeckId) -> Result<()> {
        let new_counts_towards_review_limit = self.get_root_limits().cap_new_to_review;
        self.decrement_deck_and_parent_limits(deck_id, LimitKind::New)?;
        if new_counts_towards_review_limit {
            self.decrement_deck_and_parent_limits(deck_id, LimitKind::Review)?;
        }
        Ok(())
    }

    fn decrement_node_and_parent_limits(&mut self, node_id: &NodeId, kind: LimitKind, count: u32) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().cloned();

        let limits = &mut node.data_mut().limits;
        if limits.decrement(kind, count).count_reached_zero {
            let limits = *limits;
            self.cap_node_and_descendants(node_id, limits);
        };

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_limits(&parent_id, kind, count)
        }
    }

    fn decrement_rwkv_review_minimum(&mut self, node_id: &NodeId, count: u32) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().cloned();
        let minimum = &mut node.data_mut().rwkv_review_minimum;
        *minimum = minimum.saturating_sub(count);
        if let Some(parent_id) = parent {
            self.decrement_rwkv_review_minimum(&parent_id, count);
        }
    }

    fn cap_node_and_descendants(&mut self, node_id: &NodeId, limits: RemainingLimits) {
        let node = self.tree.get_mut(node_id).unwrap();
        node.data_mut().limits.cap_to(limits);
        for child_id in node.children().clone() {
            self.cap_node_and_descendants(&child_id, limits);
        }
    }
}
