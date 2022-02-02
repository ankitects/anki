// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use id_tree::{InsertBehavior, Node, NodeId, Tree, TreeBuilder};

use super::{BuryMode, QueueSortOptions};
use crate::{
    deckconfig::NewCardSortOrder, decks::limits::RemainingLimits, prelude::*,
    scheduler::timing::SchedTimingToday,
};

/// Data container and helper for building queues.
#[derive(Debug, Clone)]
pub(super) struct Context {
    pub(super) timing: SchedTimingToday,
    config_map: HashMap<DeckConfigId, DeckConfig>,
    /// The active decks.
    pub(super) decks: Vec<Deck>,
    pub(super) limits: LimitTreeMap,
    pub(super) sort_options: QueueSortOptions,
    deck_map: HashMap<DeckId, Deck>,
}

#[derive(Debug, Clone)]
pub(super) struct LimitTreeMap {
    /// A tree representing the remaining limits of the active deck hierarchy.
    //
    // As long as we never (1) allow a tree without a root and (2) remove nodes,
    // it's safe to unwrap on Tree::get() and Tree::root_node_id(), even if we
    // clone Nodes.
    tree: Tree<RemainingLimits>,
    /// A map to access the tree node of a deck. Only decks with a remaining
    /// limit above zero are included.
    map: HashMap<DeckId, NodeId>,
    initial_root_limits: RemainingLimits,
}

impl LimitTreeMap {
    /// Returns the newly built [LimitTreeMap] and the represented decks in depth-first order.
    fn build(
        col: &mut Collection,
        deck_id: DeckId,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
    ) -> Result<(Self, Vec<Deck>)> {
        let mut decks = vec![col.storage.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?];

        let root_config = decks[0].config_id().and_then(|id| config.get(&id));
        let initial_root_limits = RemainingLimits::new(&decks[0], root_config, today, true);
        let tree = TreeBuilder::new()
            .with_root(Node::new(initial_root_limits))
            .build();

        let parent_node_id = tree.root_node_id().unwrap().clone();
        let mut map = HashMap::new();
        map.insert(deck_id, parent_node_id.clone());

        let mut limits = Self {
            tree,
            map,
            initial_root_limits,
        };
        decks = limits.add_descendant_nodes(
            col,
            &parent_node_id,
            initial_root_limits,
            decks,
            config,
            today,
        )?;

        Ok((limits, decks))
    }

    /// Recursively appends all descendants to the provided [NodeMut], adding their
    /// [NodeId]s to the [HashMap] and appending their [Deck]s to the [Vec<Deck>],
    /// which is returned.
    ///
    /// The [NodeMut] is assumed to represent the last [Deck] in the [Vec<Deck>].
    /// [RemainingLimits] are capped to their parent's limits.
    /// [Deck]s with empty review limits are _not_ added to the [HashMap].
    fn add_descendant_nodes(
        &mut self,
        col: &mut Collection,
        parent_node_id: &NodeId,
        parent_limits: RemainingLimits,
        mut decks: Vec<Deck>,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
    ) -> Result<Vec<Deck>> {
        for child_deck in col.storage.immediate_child_decks(&decks[decks.len() - 1])? {
            let mut child_limits = RemainingLimits::new(
                &child_deck,
                child_deck.config_id().and_then(|id| config.get(&id)),
                today,
                true,
            );
            child_limits.cap_to(parent_limits);

            let child_node_id = self
                .tree
                .insert(
                    Node::new(child_limits),
                    InsertBehavior::UnderNode(&parent_node_id),
                )
                .unwrap();
            if child_limits.review > 0 {
                self.map.insert(child_deck.id, child_node_id.clone());
            }

            decks.push(child_deck);
            decks =
                self.add_descendant_nodes(col, &child_node_id, child_limits, decks, config, today)?;
        }

        Ok(decks)
    }
}

impl Context {
    pub(super) fn new(col: &mut Collection, deck_id: DeckId) -> Result<Self> {
        let timing = col.timing_for_timestamp(TimestampSecs::now())?;
        let config_map = col.storage.get_deck_config_map()?;
        let (limits, decks) = LimitTreeMap::build(col, deck_id, &config_map, timing.days_elapsed)?;
        let sort_options = sort_options(&decks[0], &config_map);
        let deck_map = col.storage.get_decks_map()?;

        Ok(Self {
            timing,
            config_map,
            decks,
            limits,
            sort_options,
            deck_map,
        })
    }

    pub(super) fn root_deck(&self) -> &Deck {
        &self.decks[0]
    }

    pub(super) fn active_deck_ids(&self) -> Vec<DeckId> {
        self.decks.iter().map(|deck| deck.id).collect()
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
    pub(super) fn is_exhausted_root(&self) -> bool {
        self.map.is_empty()
    }

    pub(super) fn is_exhausted(&self, deck_id: DeckId) -> bool {
        self.map.get(&deck_id).is_some()
    }

    pub(super) fn remaining_node_id(&self, deck_id: DeckId) -> Option<NodeId> {
        self.map.get(&deck_id).map(Clone::clone)
    }

    pub(super) fn decrement_node_and_parent_review(&mut self, node_id: &NodeId) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().map(Clone::clone);

        let mut limit = node.data_mut();
        limit.review -= 1;
        if limit.review < 1 {
            self.remove_node_and_descendants_from_map(node_id);
        }

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_review(&parent_id)
        }
    }

    pub(super) fn decrement_node_and_parent_new(&mut self, node_id: &NodeId) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().map(Clone::clone);

        let mut limit = node.data_mut();
        limit.new -= 1;
        if limit.new < 1 {
            self.remove_node_and_descendants_from_map(node_id);
        }

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_new(&parent_id)
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
        let limit = node.data_mut();
        limit.new = limit.new.min(limit.review).min(parent_limit);
        let node_limit = limit.new;

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
