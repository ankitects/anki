// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    iter::Peekable,
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
    }
    for child in &mut node.children {
        add_counts(child, counts);
    }
}

/// Apply parent limits to children, and add child counts to parents.
fn apply_limits_v1(
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
        apply_limits_v1(child, limits, remaining);
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
fn apply_limits_v2(
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
        child_rev_total += apply_limits_v2(child, limits, remaining);
        child_new_total += child.new_count;
        // no limit on learning cards
        node.learn_count += child.learn_count;
    }

    // add child counts to our count, capped to remaining limit
    node.new_count = (node.new_count + child_new_total).min(remaining.new);
    node.review_count = (node.review_count + child_rev_total).min(remaining.review);

    original_rev_count + child_rev_total
}

/// Add child counts, then limit to remaining limit. The v3 scheduler does not
/// propagate limits down the tree. Limits for a deck affect only the amount
/// that deck itself will gather.
/// The v3 scheduler also caps the new limit to the remaining review limit,
/// so no new cards will be introduced when there is a backlog that exceeds
/// the review limits.
fn apply_limits_v3(
    node: &mut DeckTreeNode,
    limits: &HashMap<DeckId, RemainingLimits>,
) -> (u32, u32) {
    let mut remaining = limits
        .get(&DeckId(node.deck_id))
        .copied()
        .unwrap_or_default();

    // recurse into children, tallying their counts
    let mut child_new_total = 0;
    let mut child_rev_total = 0;
    for child in &mut node.children {
        let child_counts = apply_limits_v3(child, limits);
        child_new_total += child_counts.0;
        child_rev_total += child_counts.1;
        // no limit on learning cards
        node.learn_count += child.learn_count;
    }

    // new limits capped to review limits
    remaining.new = remaining.new.min(
        remaining
            .review
            .saturating_sub(node.review_count)
            .saturating_sub(child_rev_total),
    );

    // parents want the child total without caps
    let out = (
        node.new_count.min(remaining.new) + child_new_total,
        node.review_count.min(remaining.review) + child_rev_total,
    );

    // but the current node needs to cap after adding children
    node.new_count = (node.new_count + child_new_total).min(remaining.new);
    node.review_count = (node.review_count + child_rev_total).min(remaining.review);

    out
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
            let counts = self.due_counts(days_elapsed, learn_cutoff, limit, v3)?;
            let dconf = self.storage.get_deck_config_map()?;
            add_counts(&mut tree, &counts);
            let limits = remaining_limits_map(decks_map.values(), &dconf, days_elapsed);
            if sched_ver == SchedulerVersion::V2 {
                if v3 {
                    apply_limits_v3(&mut tree, &limits);
                } else {
                    apply_limits_v2(&mut tree, &limits, RemainingLimits::default());
                }
            } else {
                apply_limits_v1(&mut tree, &limits, RemainingLimits::default());
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
