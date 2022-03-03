// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, iter::Peekable};

use id_tree::{InsertBehavior, Node, NodeId, Tree};

use super::Deck;
use crate::{
    deckconfig::{DeckConfig, DeckConfigId},
    prelude::*,
};

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct RemainingLimits {
    pub review: u32,
    pub new: u32,
}

impl RemainingLimits {
    pub(crate) fn new(deck: &Deck, config: Option<&DeckConfig>, today: u32, v3: bool) -> Self {
        config
            .map(|config| {
                let (new_today, mut rev_today) = deck.new_rev_counts(today);
                if v3 {
                    // any reviewed new cards contribute to the review limit
                    rev_today += new_today;
                }
                RemainingLimits {
                    review: ((config.inner.reviews_per_day as i32) - rev_today).max(0) as u32,
                    new: ((config.inner.new_per_day as i32) - new_today).max(0) as u32,
                }
            })
            .unwrap_or_default()
    }

    pub(crate) fn cap_to(&mut self, limits: RemainingLimits) {
        self.review = self.review.min(limits.review);
        self.new = self.new.min(limits.new);
    }
}

impl Default for RemainingLimits {
    fn default() -> Self {
        RemainingLimits {
            review: 9999,
            new: 9999,
        }
    }
}

pub(crate) fn remaining_limits_map<'a>(
    decks: impl Iterator<Item = &'a Deck>,
    config: &'a HashMap<DeckConfigId, DeckConfig>,
    today: u32,
    v3: bool,
) -> HashMap<DeckId, RemainingLimits> {
    decks
        .map(|deck| {
            (
                deck.id,
                RemainingLimits::new(
                    deck,
                    deck.config_id().and_then(|id| config.get(&id)),
                    today,
                    v3,
                ),
            )
        })
        .collect()
}

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

#[derive(Debug, Clone)]
pub(crate) struct LimitTreeMap {
    /// A tree representing the remaining limits of the active deck hierarchy.
    //
    // As long as we never (1) allow a tree without a root, (2) remove nodes,
    // and (3) have more than 1 tree, it's safe to unwrap on Tree::get() and
    // Tree::root_node_id(), even if we clone Nodes.
    tree: Tree<NodeLimits>,
    /// A map to access the tree node of a deck. Only decks with a remaining
    /// limit above zero are included.
    map: HashMap<DeckId, NodeId>,
}

impl LimitTreeMap {
    /// Child [Deck]s must be sorted by name.
    pub(crate) fn build(
        root_deck: &Deck,
        child_decks: Vec<Deck>,
        config: &HashMap<DeckConfigId, DeckConfig>,
        today: u32,
    ) -> Self {
        let root_limits = NodeLimits::new(root_deck, config, today);
        let mut tree = Tree::new();
        let root_id = tree
            .insert(Node::new(root_limits), InsertBehavior::AsRoot)
            .unwrap();

        let mut map = HashMap::new();
        map.insert(root_deck.id, root_id.clone());

        let mut limits = Self { tree, map };
        let mut remaining_decks = child_decks.into_iter().peekable();
        limits.add_child_nodes(root_id, &mut remaining_decks, config, today);

        limits
    }

    /// Recursively appends descendants to the provided parent [Node], and adds
    /// them to the [HashMap].
    /// Given [Deck]s are assumed to arrive in depth-first order.
    /// The tree-from-deck-list logic is taken from [crate::decks::tree::add_child_nodes].
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
    pub(crate) fn root_limit_reached(&self) -> bool {
        self.map.is_empty()
    }

    pub(crate) fn limit_reached(&self, deck_id: DeckId) -> bool {
        self.map.get(&deck_id).is_none()
    }

    pub(crate) fn active_decks(&self) -> Vec<DeckId> {
        self.tree
            .traverse_pre_order(self.tree.root_node_id().unwrap())
            .unwrap()
            .map(|node| node.data().deck_id)
            .collect()
    }

    pub(crate) fn remaining_node_id(&self, deck_id: DeckId) -> Option<NodeId> {
        self.map.get(&deck_id).map(Clone::clone)
    }

    pub(crate) fn decrement_node_and_parent_limits(&mut self, node_id: &NodeId, new: bool) {
        let node = self.tree.get_mut(node_id).unwrap();
        let parent = node.parent().cloned();

        let limit = &mut node.data_mut().limits;
        if if new {
            limit.new = limit.new.saturating_sub(1);
            limit.new
        } else {
            limit.review = limit.review.saturating_sub(1);
            limit.review
        } == 0
        {
            self.remove_node_and_descendants_from_map(node_id);
        };

        if let Some(parent_id) = parent {
            self.decrement_node_and_parent_limits(&parent_id, new)
        }
    }

    pub(crate) fn remove_node_and_descendants_from_map(&mut self, node_id: &NodeId) {
        let node = self.tree.get(node_id).unwrap();
        self.map.remove(&node.data().deck_id);

        for child_id in node.children().clone() {
            self.remove_node_and_descendants_from_map(&child_id);
        }
    }

    pub(crate) fn cap_new_to_review(&mut self) {
        self.cap_new_to_review_rec(&self.tree.root_node_id().unwrap().clone(), 9999);
    }

    fn cap_new_to_review_rec(&mut self, node_id: &NodeId, parent_limit: u32) {
        let node = self.tree.get_mut(node_id).unwrap();
        let mut limits = &mut node.data_mut().limits;
        limits.new = limits.new.min(limits.review).min(parent_limit);

        // clone because of borrowing rules
        let node_limit = limits.new;
        let children = node.children().clone();

        if node_limit == 0 {
            self.remove_node_and_descendants_from_map(node_id);
        }

        for child_id in children {
            self.cap_new_to_review_rec(&child_id, node_limit);
        }
    }
}
