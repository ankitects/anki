// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::iter::Peekable;

use anki_proto::decks::deck::normal::DayLimit;
use id_tree::InsertBehavior;
use id_tree::Node;
use id_tree::NodeId;
use id_tree::Tree;

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
    fn decrement(&mut self, kind: LimitKind) -> DecrementResult {
        let before = *self;
        match kind {
            LimitKind::Review => {
                self.review = self.review.saturating_sub(1);
                if self.cap_new_to_review {
                    self.new = self.new.min(self.review);
                }
            }
            LimitKind::New => self.new = self.new.saturating_sub(1),
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
    /// absolute level in the deck hierarchy
    level: usize,
    limits: RemainingLimits,
}

impl NodeLimits {
    fn new(
        deck: &Deck,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
    ) -> Self {
        Self {
            level: deck.name.components().count(),
            limits: RemainingLimits::new(
                deck,
                deck.config_id().and_then(|id| config.get(&id)),
                today,
                new_cards_ignore_review_limit,
            ),
        }
    }
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
    ) -> Self {
        let root_limits = NodeLimits::new(&decks[0], config, today, new_cards_ignore_review_limit);
        let mut tree = Tree::new();
        let root_id = tree
            .insert(Node::new(root_limits), InsertBehavior::AsRoot)
            .unwrap();

        let mut map = HashMap::new();
        map.insert(decks[0].id, root_id.clone());

        let mut limits = Self { tree, map };
        let mut remaining_decks = decks[1..].iter().peekable();
        limits.add_child_nodes(
            root_id,
            &mut remaining_decks,
            config,
            today,
            new_cards_ignore_review_limit,
        );

        limits
    }

    /// Recursively appends descendants to the provided parent [Node], and adds
    /// them to the [HashMap].
    /// Given [Deck]s are assumed to arrive in depth-first order.
    /// The tree-from-deck-list logic is taken from
    /// [crate::decks::tree::add_child_nodes].
    fn add_child_nodes<'d>(
        &mut self,
        parent_node_id: NodeId,
        remaining_decks: &mut Peekable<impl Iterator<Item = &'d Deck>>,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
    ) {
        let parent = *self.tree.get(&parent_node_id).unwrap().data();
        while let Some(deck) = remaining_decks.peek() {
            match deck.name.components().count() {
                l if l <= parent.level => {
                    // next item is at a higher level
                    break;
                }
                l if l == parent.level + 1 => {
                    // next item is an immediate descendent of parent
                    self.insert_child_node(
                        deck,
                        parent_node_id.clone(),
                        config,
                        today,
                        new_cards_ignore_review_limit,
                    );
                    remaining_decks.next();
                }
                _ => {
                    // next item is at a lower level
                    if let Some(last_child_node_id) = self
                        .tree
                        .get(&parent_node_id)
                        .unwrap()
                        .children()
                        .last()
                        .cloned()
                    {
                        self.add_child_nodes(
                            last_child_node_id,
                            remaining_decks,
                            config,
                            today,
                            new_cards_ignore_review_limit,
                        )
                    } else {
                        // immediate parent is missing, skip the deck until a DB check is run
                        remaining_decks.next();
                    }
                }
            }
        }
    }

    fn insert_child_node(
        &mut self,
        child_deck: &Deck,
        parent_node_id: NodeId,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
        new_cards_ignore_review_limit: bool,
    ) {
        let mut child_limits =
            NodeLimits::new(child_deck, config, today, new_cards_ignore_review_limit);
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
        self.map.insert(child_deck.id, child_node_id);
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

    pub(crate) fn get_root_limits(&self) -> RemainingLimits {
        self.get_node_limits(self.tree.root_node_id().unwrap())
    }

    pub(crate) fn root_limit_reached(&self, kind: LimitKind) -> bool {
        self.get_root_limits().get(kind) == 0
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
        self.decrement_node_and_parent_limits(&node_id, kind);
        Ok(())
    }

    fn decrement_node_and_parent_limits(&mut self, node_id: &NodeId, kind: LimitKind) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().cloned();

        let limits = &mut node.data_mut().limits;
        if limits.decrement(kind).count_reached_zero {
            let limits = *limits;
            self.cap_node_and_descendants(node_id, limits);
        };

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_limits(&parent_id, kind)
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
