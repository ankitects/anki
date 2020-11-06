// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{Deck, DeckKind, DueCounts};
use crate::{
    backend_proto::DeckTreeNode,
    collection::Collection,
    deckconf::{DeckConf, DeckConfID},
    decks::DeckID,
    err::Result,
    timestamp::TimestampSecs,
};
use serde_tuple::Serialize_tuple;
use std::{
    collections::{HashMap, HashSet},
    iter::Peekable,
};
use unicase::UniCase;

fn deck_names_to_tree(names: Vec<(DeckID, String)>) -> DeckTreeNode {
    let mut top = DeckTreeNode::default();
    let mut it = names.into_iter().peekable();

    add_child_nodes(&mut it, &mut top);

    top
}

fn add_child_nodes(
    names: &mut Peekable<impl Iterator<Item = (DeckID, String)>>,
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
    decks: &HashMap<DeckID, Deck>,
    browser: bool,
) {
    if let Some(deck) = decks.get(&DeckID(node.deck_id)) {
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

fn add_counts(node: &mut DeckTreeNode, counts: &HashMap<DeckID, DueCounts>) {
    if let Some(counts) = counts.get(&DeckID(node.deck_id)) {
        node.new_count = counts.new;
        node.review_count = counts.review;
        node.learn_count = counts.learning;
    }
    for child in &mut node.children {
        add_counts(child, counts);
    }
}

/// Apply parent limits to children, and add child counts to parents.
/// Counts are (new, review).
fn apply_limits(
    node: &mut DeckTreeNode,
    today: u32,
    decks: &HashMap<DeckID, Deck>,
    dconf: &HashMap<DeckConfID, DeckConf>,
    parent_limits: (u32, u32),
) {
    let (mut remaining_new, mut remaining_rev) =
        remaining_counts_for_deck(DeckID(node.deck_id), today, decks, dconf);

    // cap remaining to parent limits
    remaining_new = remaining_new.min(parent_limits.0);
    remaining_rev = remaining_rev.min(parent_limits.1);

    // apply our limit to children and tally their counts
    let mut child_new_total = 0;
    let mut child_rev_total = 0;
    for child in &mut node.children {
        apply_limits(child, today, decks, dconf, (remaining_new, remaining_rev));
        child_new_total += child.new_count;
        child_rev_total += child.review_count;
        // no limit on learning cards
        node.learn_count += child.learn_count;
    }

    // add child counts to our count, capped to remaining limit
    node.new_count = (node.new_count + child_new_total).min(remaining_new);
    node.review_count = (node.review_count + child_rev_total).min(remaining_rev);
}

fn remaining_counts_for_deck(
    did: DeckID,
    today: u32,
    decks: &HashMap<DeckID, Deck>,
    dconf: &HashMap<DeckConfID, DeckConf>,
) -> (u32, u32) {
    if let Some(deck) = decks.get(&did) {
        match &deck.kind {
            DeckKind::Normal(norm) => {
                let (new_today, rev_today) = deck.new_rev_counts(today);
                if let Some(conf) = dconf
                    .get(&DeckConfID(norm.config_id))
                    .or_else(|| dconf.get(&DeckConfID(1)))
                {
                    let new = (conf.inner.new_per_day as i32)
                        .saturating_sub(new_today)
                        .max(0);
                    let rev = (conf.inner.reviews_per_day as i32)
                        .saturating_sub(rev_today)
                        .max(0);
                    (new as u32, rev as u32)
                } else {
                    // missing dconf and fallback
                    (0, 0)
                }
            }
            DeckKind::Filtered(_) => {
                // filtered decks have no limit
                (std::u32::MAX, std::u32::MAX)
            }
        }
    } else {
        // top level deck with id 0
        (std::u32::MAX, std::u32::MAX)
    }
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

fn get_subnode(top: DeckTreeNode, target: DeckID) -> Option<DeckTreeNode> {
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
        top_deck_id: Option<DeckID>,
    ) -> Result<DeckTreeNode> {
        let names = self.storage.get_all_deck_names()?;
        let mut tree = deck_names_to_tree(names);

        let decks_map: HashMap<_, _> = self
            .storage
            .get_all_decks()?
            .into_iter()
            .map(|d| (d.id, d))
            .collect();

        add_collapsed_and_filtered(&mut tree, &decks_map, now.is_none());
        if self.default_deck_is_empty()? {
            hide_default_deck(&mut tree);
        }

        if let Some(now) = now {
            let limit = top_deck_id.and_then(|did| {
                if let Some(deck) = decks_map.get(&did) {
                    Some(deck.name.as_str())
                } else {
                    None
                }
            });
            let days_elapsed = self.timing_for_timestamp(now)?.days_elapsed;
            let learn_cutoff = (now.0 as u32) + self.learn_ahead_secs();
            let counts = self.due_counts(days_elapsed, learn_cutoff, limit)?;
            let dconf: HashMap<_, _> = self
                .storage
                .all_deck_config()?
                .into_iter()
                .map(|d| (d.id, d))
                .collect();
            add_counts(&mut tree, &counts);
            apply_limits(
                &mut tree,
                days_elapsed,
                &decks_map,
                &dconf,
                (std::u32::MAX, std::u32::MAX),
            );
        }

        Ok(tree)
    }

    pub fn current_deck_tree(&mut self) -> Result<Option<DeckTreeNode>> {
        let target = self.get_current_deck_id();
        let tree = self.deck_tree(Some(TimestampSecs::now()), Some(target))?;
        Ok(get_subnode(tree, target))
    }

    pub(crate) fn legacy_deck_tree(&mut self) -> Result<LegacyDueCounts> {
        let tree = self.deck_tree(Some(TimestampSecs::now()), None)?;
        Ok(LegacyDueCounts::from(tree))
    }

    pub(crate) fn add_missing_deck_names(&mut self, names: &[(DeckID, String)]) -> Result<usize> {
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
    use crate::{collection::open_test_collection, deckconf::DeckConfID, err::Result};

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
        note.fields[0] = "{{c1::}} {{c2::}} {{c3::}} {{c4::}}".into();
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
        let mut conf = col.get_deck_config(DeckConfID(1), false)?.unwrap();
        conf.inner.new_per_day = 4;
        col.add_or_update_deck_config(&mut conf, false)?;

        let tree = col.deck_tree(Some(TimestampSecs::now()), None)?;
        assert_eq!(tree.children[0].new_count, 3);
        assert_eq!(tree.children[0].children[0].new_count, 3);

        Ok(())
    }
}
