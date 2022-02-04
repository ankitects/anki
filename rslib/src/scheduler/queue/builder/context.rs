// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, iter::Peekable};

use id_tree::{InsertBehavior, Node, NodeId, Tree};

use super::{BuryMode, QueueSortOptions};
use crate::{
    deckconfig::NewCardSortOrder, decks::limits::RemainingLimits, prelude::*,
    scheduler::timing::SchedTimingToday,
};

/// Wrapper of [RemainingLimits] with some additional meta data.
#[derive(Debug, Clone, Copy)]
struct NodeLimits {
    deck_id: DeckId,
    /// absolute level in the deck hierarchy
    level: usize,
    limits: RemainingLimits,
}

impl NodeLimits {
    fn new(deck: &Deck, config: &HashMap<DeckConfigId, DeckConfig>, today: u32) -> Self {
        Self {
            deck_id: deck.id,
            level: deck.name.components().count(),
            limits: RemainingLimits::new(
                deck,
                deck.config_id().and_then(|id| config.get(&id)),
                today,
                true,
            ),
        }
    }
}

/// Data container and helper for building queues.
#[derive(Debug, Clone)]
pub(super) struct Context {
    pub(super) timing: SchedTimingToday,
    config_map: HashMap<DeckConfigId, DeckConfig>,
    /// The active decks.
    pub(super) root_deck: Deck,
    pub(super) limits: LimitTreeMap,
    pub(super) sort_options: QueueSortOptions,
    deck_map: HashMap<DeckId, Deck>,
}

#[derive(Debug, Clone)]
pub(super) struct LimitTreeMap {
    /// A tree representing the remaining limits of the active deck hierarchy.
    //
    // As long as we never (1) allow a tree without a root, (2) remove nodes,
    // and (3) have more than 1 tree, it's safe to unwrap on Tree::get() and
    // Tree::root_node_id(), even if we clone Nodes.
    tree: Tree<NodeLimits>,
    /// A map to access the tree node of a deck. Only decks with a remaining
    /// limit above zero are included.
    map: HashMap<DeckId, NodeId>,
    initial_root_limits: RemainingLimits,
}

impl LimitTreeMap {
    /// Returns the newly built [LimitTreeMap] and the deck for `deck_id`.
    fn build(
        col: &mut Collection,
        deck_id: DeckId,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
    ) -> Result<(Self, Deck)> {
        let root_deck = col.storage.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?;
        let root_limits = NodeLimits::new(&root_deck, config, today);
        let initial_root_limits = root_limits.limits;

        let mut tree = Tree::new();
        let root_id = tree
            .insert(Node::new(root_limits), InsertBehavior::AsRoot)
            .unwrap();

        let mut map = HashMap::new();
        map.insert(deck_id, root_id.clone());

        let mut limits = Self {
            tree,
            map,
            initial_root_limits,
        };
        let mut remaining_decks = col.storage.child_decks(&root_deck)?.into_iter().peekable();
        limits.add_child_nodes(root_id, &mut remaining_decks, config, today);

        Ok((limits, root_deck))
    }

    /// Recursively appends descendants to the provided parent [Node], and adds
    /// them to the [HashMap].
    /// Given [Deck]s are assumed to arrive in depth-first order.
    /// The tree-from-deck-list logic to is taken from [crate::decks::tree::add_child_nodes].
    fn add_child_nodes(
        &mut self,
        parent_node_id: NodeId,
        remaining_decks: &mut Peekable<impl Iterator<Item = Deck>>,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
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
                    self.insert_child_node(deck, parent_node_id.clone(), config, today);
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
                        self.add_child_nodes(last_child_node_id, remaining_decks, config, today)
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
    ) {
        let mut child_limits = NodeLimits::new(child_deck, config, today);
        child_limits
            .limits
            .cap_to(self.tree.get(&parent_node_id).unwrap().data().limits);

        let child_node_id = self
            .tree
            .insert(
                Node::new(child_limits),
                InsertBehavior::UnderNode(&parent_node_id),
            )
            .unwrap();
        if child_limits.limits.review > 0 {
            self.map.insert(child_deck.id, child_node_id);
        }
    }
}

impl Context {
    pub(super) fn new(col: &mut Collection, deck_id: DeckId) -> Result<Self> {
        let timing = col.timing_for_timestamp(TimestampSecs::now())?;
        let config_map = col.storage.get_deck_config_map()?;
        let (limits, root_deck) =
            LimitTreeMap::build(col, deck_id, &config_map, timing.days_elapsed)?;
        let sort_options = sort_options(&root_deck, &config_map);
        let deck_map = col.storage.get_decks_map()?;

        Ok(Self {
            timing,
            config_map,
            root_deck,
            limits,
            sort_options,
            deck_map,
        })
    }

    pub(super) fn bury_mode(&self, deck_id: DeckId) -> BuryMode {
        self.deck_map
            .get(&deck_id)
            .and_then(|deck| deck.config_id())
            .and_then(|config_id| self.config_map.get(&config_id))
            .map(|config| BuryMode {
                bury_new: config.inner.bury_new,
                bury_reviews: config.inner.bury_reviews,
            })
            .unwrap_or_default()
    }
}

impl LimitTreeMap {
    pub(super) fn root_limit_reached(&self) -> bool {
        self.map.is_empty()
    }

    pub(super) fn limit_reached(&self, deck_id: DeckId) -> bool {
        self.map.get(&deck_id).is_none()
    }

    pub(super) fn remaining_decks(&self) -> Vec<DeckId> {
        self.map.keys().copied().collect()
    }

    pub(super) fn remaining_node_id(&self, deck_id: DeckId) -> Option<NodeId> {
        self.map.get(&deck_id).map(Clone::clone)
    }

    pub(super) fn decrement_node_and_parent_limits(&mut self, node_id: &NodeId, new: bool) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().cloned();

        let limit = &mut node.data_mut().limits;
        if if new {
            limit.new = limit.new.saturating_sub(1);
            limit.new
        } else {
            limit.review = limit.review.saturating_sub(1);
            limit.review
        } < 1
        {
            self.remove_node_and_descendants_from_map(node_id);
        };

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_limits(&parent_id, new)
        }
    }

    pub(super) fn remove_node_and_descendants_from_map(&mut self, node_id: &NodeId) {
        let node = self.tree.get(node_id).unwrap();
        self.map.remove(&node.data().deck_id);

        for child_id in node.children().clone() {
            self.remove_node_and_descendants_from_map(&child_id);
        }
    }

    pub(super) fn cap_new_to_review(&mut self) {
        self.cap_new_to_review_rec(&self.tree.root_node_id().unwrap().clone(), 9999);
    }

    fn cap_new_to_review_rec(&mut self, node_id: &NodeId, parent_limit: u32) {
        let node = self.tree.get_mut(node_id).unwrap();
        let mut limits = node.data_mut().limits;
        limits.new = limits.new.min(limits.review).min(parent_limit);
        let node_limit = limits.new;

        for child_id in node.children().clone() {
            self.cap_new_to_review_rec(&child_id, node_limit);
        }
    }

    /// The configured review and new limits of the root deck, but with the new
    /// limit capped to the remaining reviews.
    pub(super) fn final_limits(&self) -> RemainingLimits {
        RemainingLimits {
            new: self.initial_root_limits.new.min(
                self.tree
                    .get(self.tree.root_node_id().unwrap())
                    .unwrap()
                    .data()
                    .limits
                    .review,
            ),
            ..self.initial_root_limits
        }
    }
}

fn sort_options(deck: &Deck, config_map: &HashMap<DeckConfigId, DeckConfig>) -> QueueSortOptions {
    deck.config_id()
        .and_then(|config_id| config_map.get(&config_id))
        .map(|config| QueueSortOptions {
            new_order: config.inner.new_card_sort_order(),
            new_gather_priority: config.inner.new_card_gather_priority(),
            review_order: config.inner.review_order(),
            day_learn_mix: config.inner.interday_learning_mix(),
            new_review_mix: config.inner.new_mix(),
        })
        .unwrap_or_else(|| {
            // filtered decks do not space siblings
            QueueSortOptions {
                new_order: NewCardSortOrder::LowestPosition,
                ..Default::default()
            }
        })
}
