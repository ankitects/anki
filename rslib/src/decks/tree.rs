// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    iter::Peekable,
    ops::AddAssign,
};

use serde_tuple::Serialize_tuple;
use unicase::UniCase;

use super::{
    limits::{remaining_limits_map, RemainingLimits},
    DueCounts,
};
pub use crate::backend_proto::set_deck_collapsed_request::Scope as DeckCollapseScope;
use crate::{
    backend_proto::DeckTreeNode, config::SchedulerVersion, ops::OpOutput, prelude::*, undo::Op,
};

fn deck_names_to_tree(names: impl Iterator<Item = (DeckId, String)>) -> DeckTreeNode {
    let mut top = DeckTreeNode::default();
    let mut it = names.peekable();

    add_child_nodes(&mut it, &mut top);

    top
}

fn add_child_nodes(
    names: &mut Peekable<impl Iterator<Item = (DeckId, String)>>,
    parent: &mut DeckTreeNode,
) {
    while let Some((id, name)) = names.peek() {
        let split_name: Vec<_> = name.split("::").collect();
        match split_name.len() as u32 {
            l if l <= parent.level => {
                // next item is at a higher level
                return;
            }
            l if l == parent.level + 1 => {
                // next item is an immediate descendent of parent
                parent.children.push(DeckTreeNode {
                    deck_id: id.0,
                    name: (*split_name.last().unwrap()).into(),
                    children: vec![],
                    level: parent.level + 1,
                    ..Default::default()
                });
                names.next();
            }
            _ => {
                // next item is at a lower level
                if let Some(last_child) = parent.children.last_mut() {
                    add_child_nodes(names, last_child)
                } else {
                    // immediate parent is missing, skip the deck until a DB check is run
                    names.next();
                }
            }
        }
    }
}

fn add_collapsed_and_filtered(
    node: &mut DeckTreeNode,
    decks: &HashMap<DeckId, Deck>,
    browser: bool,
) {
    if let Some(deck) = decks.get(&DeckId(node.deck_id)) {
        node.collapsed = if browser {
            deck.common.browser_collapsed
        } else {
            deck.common.study_collapsed
        };
        node.filtered = deck.is_filtered();
    }
    for child in &mut node.children {
        add_collapsed_and_filtered(child, decks, browser);
    }
}

fn add_counts(node: &mut DeckTreeNode, counts: &HashMap<DeckId, DueCounts>) {
    if let Some(counts) = counts.get(&DeckId(node.deck_id)) {
        node.new_count = counts.new;
        node.review_count = counts.review;
        node.learn_count = counts.learning;
        node.intraday_learning = counts.intraday_learning;
        node.interday_learning_uncapped = counts.interday_learning;
        node.new_uncapped = counts.new;
        node.review_uncapped = counts.review;
        node.total_in_deck = counts.total_cards;
    }
    for child in &mut node.children {
        add_counts(child, counts);
    }
}

/// Apply parent limits to children, and add child counts to parents.
fn sum_counts_and_apply_limits_v1(
    node: &mut DeckTreeNode,
    limits: &HashMap<DeckId, RemainingLimits>,
    parent_limits: RemainingLimits,
) {
    let mut remaining = limits
        .get(&DeckId(node.deck_id))
        .copied()
        .unwrap_or_default();
    remaining.cap_to(parent_limits);

    // apply our limit to children and tally their counts
    let mut child_new_total = 0;
    let mut child_rev_total = 0;
    for child in &mut node.children {
        sum_counts_and_apply_limits_v1(child, limits, remaining);
        child_new_total += child.new_count;
        child_rev_total += child.review_count;
        // no limit on learning cards
        node.learn_count += child.learn_count;
    }

    // add child counts to our count, capped to remaining limit
    node.new_count = (node.new_count + child_new_total).min(remaining.new);
    node.review_count = (node.review_count + child_rev_total).min(remaining.review);
}

/// Apply parent new limits to children, and add child counts to parents. Unlike
/// v1, reviews are not capped by their parents, and we
/// return the uncapped review amount to add to the parent.
fn sum_counts_and_apply_limits_v2(
    node: &mut DeckTreeNode,
    limits: &HashMap<DeckId, RemainingLimits>,
    parent_limits: RemainingLimits,
) -> u32 {
    let original_rev_count = node.review_count;
    let mut remaining = limits
        .get(&DeckId(node.deck_id))
        .copied()
        .unwrap_or_default();
    remaining.new = remaining.new.min(parent_limits.new);

    // apply our limit to children and tally their counts
    let mut child_new_total = 0;
    let mut child_rev_total = 0;
    for child in &mut node.children {
        child_rev_total += sum_counts_and_apply_limits_v2(child, limits, remaining);
        child_new_total += child.new_count;
        // no limit on learning cards
        node.learn_count += child.learn_count;
    }

    // add child counts to our count, capped to remaining limit
    node.new_count = (node.new_count + child_new_total).min(remaining.new);
    node.review_count = (node.review_count + child_rev_total).min(remaining.review);

    original_rev_count + child_rev_total
}

/// A temporary container used during count summation and limit application.
#[derive(Default, Clone)]
struct NodeCountsV3 {
    new: u32,
    review: u32,
    intraday_learning: u32,
    interday_learning: u32,
    total: u32,
}

impl NodeCountsV3 {
    fn capped(&self, remaining: &RemainingLimits) -> Self {
        let mut capped = self.clone();
        // apply review limit to interday learning
        capped.interday_learning = capped.interday_learning.min(remaining.review);
        let mut remaining_reviews = remaining.review.saturating_sub(capped.interday_learning);
        // any remaining review limit is applied to reviews
        capped.review = capped.review.min(remaining_reviews);
        remaining_reviews = remaining_reviews.saturating_sub(capped.review);
        // new cards last, capped to new and remaining review limits
        capped.new = capped.new.min(remaining_reviews).min(remaining.new);
        capped
    }
}
impl AddAssign for NodeCountsV3 {
    fn add_assign(&mut self, rhs: Self) {
        self.new += rhs.new;
        self.review += rhs.review;
        self.intraday_learning += rhs.intraday_learning;
        self.interday_learning += rhs.interday_learning;
        self.total += rhs.total;
    }
}

/// Adjust new, review and learning counts based on the daily limits.
/// As part of this process, the separate interday and intraday learning
/// counts are combined after the limits have been applied.
fn sum_counts_and_apply_limits_v3(
    node: &mut DeckTreeNode,
    limits: &HashMap<DeckId, RemainingLimits>,
) -> NodeCountsV3 {
    let remaining = limits
        .get(&DeckId(node.deck_id))
        .copied()
        .unwrap_or_default();

    // cap current node's own cards
    let this_node_uncapped = NodeCountsV3 {
        new: node.new_count,
        review: node.review_count,
        intraday_learning: node.intraday_learning,
        interday_learning: node.interday_learning_uncapped,
        total: node.total_in_deck,
    };
    let mut individually_capped_total = this_node_uncapped.capped(&remaining);
    // and add the capped values from child decks
    for child in &mut node.children {
        individually_capped_total += sum_counts_and_apply_limits_v3(child, limits);
    }
    node.total_including_children = individually_capped_total.total;

    // We already have a sum of the current deck's capped cards+its child decks'
    // capped cards, which we'll return to the parent. But because clicking on a
    // given deck imposes that deck's limits on the total number of cards shown,
    // the sum we'll display needs to be capped again by the limits of the current
    // deck.
    let total_constrained_by_current_deck = individually_capped_total.capped(&remaining);
    node.new_count = total_constrained_by_current_deck.new;
    node.review_count = total_constrained_by_current_deck.review;
    node.learn_count = total_constrained_by_current_deck.intraday_learning
        + total_constrained_by_current_deck.interday_learning;

    individually_capped_total
}

fn hide_default_deck(node: &mut DeckTreeNode) {
    for (idx, child) in node.children.iter().enumerate() {
        // we can hide the default if it has no children
        if child.deck_id == 1 && child.children.is_empty() {
            if child.level == 1 && node.children.len() == 1 {
                // can't remove if there are no other decks
            } else {
                // safe to remove
                node.children.remove(idx);
            }
            return;
        }
    }
}

fn get_subnode(top: DeckTreeNode, target: DeckId) -> Option<DeckTreeNode> {
    if top.deck_id == target.0 {
        return Some(top);
    }
    for child in top.children {
        if let Some(node) = get_subnode(child, target) {
            return Some(node);
        }
    }

    None
}

#[derive(Serialize_tuple)]
pub(crate) struct LegacyDueCounts {
    name: String,
    deck_id: i64,
    review: u32,
    learn: u32,
    new: u32,
    children: Vec<LegacyDueCounts>,
}

impl From<DeckTreeNode> for LegacyDueCounts {
    fn from(n: DeckTreeNode) -> Self {
        LegacyDueCounts {
            name: n.name,
            deck_id: n.deck_id,
            review: n.review_count,
            learn: n.learn_count,
            new: n.new_count,
            children: n.children.into_iter().map(From::from).collect(),
        }
    }
}

impl Collection {
    /// Get the deck tree.
    /// If now is provided, due counts for the provided timestamp will be populated.
    /// If top_deck_id is provided, only the node starting at the provided deck ID will
    /// have the counts populated. Currently the entire tree is returned in this case, but
    /// this may change in the future.
    pub fn deck_tree(
        &mut self,
        now: Option<TimestampSecs>,
        top_deck_id: Option<DeckId>,
    ) -> Result<DeckTreeNode> {
        let names = self.storage.get_all_deck_names()?;
        let mut tree = deck_names_to_tree(names.into_iter());

        let decks_map = self.storage.get_decks_map()?;

        add_collapsed_and_filtered(&mut tree, &decks_map, now.is_none());
        if self.default_deck_is_empty()? {
            hide_default_deck(&mut tree);
        }

        if let Some(now) = now {
            let limit = top_deck_id
                .and_then(|did| decks_map.get(&did).map(|deck| deck.name.as_native_str()));
            let days_elapsed = self.timing_for_timestamp(now)?.days_elapsed;
            let learn_cutoff = (now.0 as u32) + self.learn_ahead_secs();
            let sched_ver = self.scheduler_version();
            let v3 = self.get_config_bool(BoolKey::Sched2021);
            let counts = self.due_counts(days_elapsed, learn_cutoff, limit)?;
            let dconf = self.storage.get_deck_config_map()?;
            add_counts(&mut tree, &counts);
            let limits = remaining_limits_map(decks_map.values(), &dconf, days_elapsed);
            if sched_ver == SchedulerVersion::V2 {
                if v3 {
                    sum_counts_and_apply_limits_v3(&mut tree, &limits);
                } else {
                    sum_counts_and_apply_limits_v2(&mut tree, &limits, RemainingLimits::default());
                }
            } else {
                sum_counts_and_apply_limits_v1(&mut tree, &limits, RemainingLimits::default());
            }
        }

        Ok(tree)
    }

    pub fn current_deck_tree(&mut self) -> Result<Option<DeckTreeNode>> {
        let target = self.get_current_deck_id();
        let tree = self.deck_tree(Some(TimestampSecs::now()), Some(target))?;
        Ok(get_subnode(tree, target))
    }

    pub fn set_deck_collapsed(
        &mut self,
        did: DeckId,
        collapsed: bool,
        scope: DeckCollapseScope,
    ) -> Result<OpOutput<()>> {
        self.transact(Op::SkipUndo, |col| {
            if let Some(mut deck) = col.storage.get_deck(did)? {
                let original = deck.clone();
                let c = &mut deck.common;
                match scope {
                    DeckCollapseScope::Reviewer => c.study_collapsed = collapsed,
                    DeckCollapseScope::Browser => c.browser_collapsed = collapsed,
                };
                col.update_deck_inner(&mut deck, original, col.usn()?)?;
            }
            Ok(())
        })
    }
}

impl Collection {
    pub(crate) fn legacy_deck_tree(&mut self) -> Result<LegacyDueCounts> {
        let tree = self.deck_tree(Some(TimestampSecs::now()), None)?;
        Ok(LegacyDueCounts::from(tree))
    }

    pub(crate) fn add_missing_deck_names(&mut self, names: &[(DeckId, String)]) -> Result<usize> {
        let mut parents = HashSet::new();
        let mut missing = 0;
        for (_id, name) in names {
            parents.insert(UniCase::new(name.as_str()));
            if let Some(immediate_parent) = name.rsplitn(2, "::").nth(1) {
                let immediate_parent_uni = UniCase::new(immediate_parent);
                if !parents.contains(&immediate_parent_uni) {
                    self.get_or_create_normal_deck(immediate_parent)?;
                    parents.insert(immediate_parent_uni);
                    missing += 1;
                }
            }
        }
        Ok(missing)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, deckconfig::DeckConfigId, error::Result};

    #[test]
    fn wellformed() -> Result<()> {
        let mut col = open_test_collection();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2")?;
        col.get_or_create_normal_deck("2::a")?;
        col.get_or_create_normal_deck("2::b")?;
        col.get_or_create_normal_deck("2::c")?;
        col.get_or_create_normal_deck("2::c::A")?;
        col.get_or_create_normal_deck("3")?;

        let tree = col.deck_tree(None, None)?;

        assert_eq!(tree.children.len(), 3);

        assert_eq!(tree.children[1].name, "2");
        assert_eq!(tree.children[1].children[0].name, "a");
        assert_eq!(tree.children[1].children[2].name, "c");
        assert_eq!(tree.children[1].children[2].children[0].name, "A");

        Ok(())
    }

    #[test]
    fn malformed() -> Result<()> {
        let mut col = open_test_collection();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2::3::4")?;

        // remove the top parent and middle parent
        col.storage.remove_deck(col.get_deck_id("2")?.unwrap())?;
        col.storage.remove_deck(col.get_deck_id("2::3")?.unwrap())?;

        let tree = col.deck_tree(None, None)?;
        assert_eq!(tree.children.len(), 1);

        Ok(())
    }

    #[test]
    fn counts() -> Result<()> {
        let mut col = open_test_collection();

        let mut parent_deck = col.get_or_create_normal_deck("Default")?;
        let mut child_deck = col.get_or_create_normal_deck("Default::one")?;

        // add some new cards
        let nt = col.get_notetype_by_name("Cloze")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "{{c1::}} {{c2::}} {{c3::}} {{c4::}}")?;
        col.add_note(&mut note, child_deck.id)?;

        let tree = col.deck_tree(Some(TimestampSecs::now()), None)?;
        assert_eq!(tree.children[0].new_count, 4);
        assert_eq!(tree.children[0].children[0].new_count, 4);

        // simulate answering a card
        child_deck.common.new_studied = 1;
        col.add_or_update_deck(&mut child_deck)?;
        parent_deck.common.new_studied = 1;
        col.add_or_update_deck(&mut parent_deck)?;

        // with the default limit of 20, there should still be 4 due
        let tree = col.deck_tree(Some(TimestampSecs::now()), None)?;
        assert_eq!(tree.children[0].new_count, 4);
        assert_eq!(tree.children[0].children[0].new_count, 4);

        // set the limit to 4, which should mean 3 are left
        let mut conf = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        conf.inner.new_per_day = 4;
        col.add_or_update_deck_config(&mut conf)?;

        let tree = col.deck_tree(Some(TimestampSecs::now()), None)?;
        assert_eq!(tree.children[0].new_count, 3);
        assert_eq!(tree.children[0].children[0].new_count, 3);

        Ok(())
    }
}
