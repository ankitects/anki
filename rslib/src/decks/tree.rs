// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;
use std::iter::Peekable;
use std::ops::AddAssign;

pub use anki_proto::decks::set_deck_collapsed_request::Scope as DeckCollapseScope;
use anki_proto::decks::DeckTreeNode;
use serde_tuple::Serialize_tuple;
use unicase::UniCase;

use super::counts::ScopedDueCounts;
use super::limits::remaining_limits_map;
use super::limits::RemainingLimits;
use super::DueCounts;
use crate::ops::OpOutput;
use crate::prelude::*;
use crate::undo::Op;

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
        // protobuf refuses to decode messages with 100+ levels of nesting, and
        // broken collections with such nesting have been found in the wild
        let capped_len = split_name.len().min(99) as u32;
        match capped_len {
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
    let empty = DueCounts::default();
    let direct = counts.get(&DeckId(node.deck_id)).unwrap_or(&empty);
    node.new_count = direct.new;
    node.review_count = direct.review;
    node.learn_count = direct.learning;
    node.intraday_learning = direct.intraday_learning;
    node.interday_learning_uncapped = direct.interday_learning;
    node.new_uncapped = direct.new;
    node.review_uncapped = direct.review;
    node.total_in_deck = direct.total_cards;
    for child in &mut node.children {
        add_counts(child, counts);
    }
}

/// A temporary container used during count summation and limit application.
#[derive(Default, Clone)]
struct NodeCountsV3 {
    new: u32,
    review: u32,
    review_limit_exempt: u32,
    intraday_learning: u32,
    interday_learning: u32,
    interday_learning_limit_exempt: u32,
    total: u32,
}

impl NodeCountsV3 {
    fn capped(&self, remaining: &RemainingLimits) -> Self {
        let mut capped = self.clone();
        // apply review limit to interday learning, excluding same-day repeats
        let limited_interday = capped
            .interday_learning
            .saturating_sub(capped.interday_learning_limit_exempt)
            .min(remaining.review);
        capped.interday_learning =
            limited_interday.saturating_add(capped.interday_learning_limit_exempt);
        let mut remaining_reviews = remaining.review.saturating_sub(limited_interday);
        // any remaining review limit is applied to reviews
        let limited_reviews = capped
            .review
            .saturating_sub(capped.review_limit_exempt)
            .min(remaining_reviews);
        capped.review = limited_reviews.saturating_add(capped.review_limit_exempt);
        capped.new = capped.new.min(remaining.new);
        if remaining.cap_new_to_review {
            remaining_reviews = remaining_reviews.saturating_sub(limited_reviews);
            capped.new = capped.new.min(remaining_reviews);
        }
        capped
    }
}

impl AddAssign for NodeCountsV3 {
    fn add_assign(&mut self, rhs: Self) {
        self.new += rhs.new;
        self.review += rhs.review;
        self.review_limit_exempt += rhs.review_limit_exempt;
        self.intraday_learning += rhs.intraday_learning;
        self.interday_learning += rhs.interday_learning;
        self.interday_learning_limit_exempt += rhs.interday_learning_limit_exempt;
        self.total += rhs.total;
    }
}

/// Adjust new, review and learning counts based on the daily limits.
/// As part of this process, the separate interday and intraday learning
/// counts are combined after the limits have been applied.
fn sum_counts_and_apply_limits_v3(
    node: &mut DeckTreeNode,
    limits: &HashMap<DeckId, RemainingLimits>,
    counts: &HashMap<DeckId, DueCounts>,
    scoped_counts: &ScopedDueCounts,
    mut parent_limits: Option<RemainingLimits>,
) -> NodeCountsV3 {
    // Evaluate each study scope independently before updating its children's
    // displayed counts. A child can have a different repeat guard/history, and
    // its own study count must not replace its contribution to the parent queue.
    let scope = scoped_counts.get(&DeckId(node.deck_id));
    let counts = scope.unwrap_or(counts);
    let own_scope = scope.map(|counts| {
        add_counts(node, counts);
        let capped =
            sum_counts_and_apply_limits_v3(node, limits, counts, &HashMap::new(), parent_limits);
        let uncapped = node.review_uncapped_including_children;
        add_counts(node, counts);
        (capped, uncapped)
    });
    let mut remaining = limits
        .get(&DeckId(node.deck_id))
        .copied()
        .unwrap_or_default();
    if let Some(parent_remaining) = parent_limits {
        remaining.cap_to(parent_remaining);
        parent_limits.replace(remaining);
    }

    // initialize with this node's values
    let mut this_node_uncapped = NodeCountsV3 {
        new: node.new_count,
        review: node.review_count,
        review_limit_exempt: counts
            .get(&DeckId(node.deck_id))
            .map(|counts| counts.review_limit_exempt)
            .unwrap_or_default(),
        intraday_learning: node.intraday_learning,
        interday_learning: node.interday_learning_uncapped,
        interday_learning_limit_exempt: counts
            .get(&DeckId(node.deck_id))
            .map(|counts| counts.interday_learning_limit_exempt)
            .unwrap_or_default(),
        total: node.total_in_deck,
    };
    let mut total_including_children = node.total_in_deck;
    let mut review_uncapped_including_children = node.review_uncapped;

    // add capped child counts / uncapped total
    for child in &mut node.children {
        this_node_uncapped +=
            sum_counts_and_apply_limits_v3(child, limits, counts, scoped_counts, parent_limits);
        total_including_children += child.total_including_children;
        review_uncapped_including_children += child.review_uncapped_including_children;
    }

    let (this_node_capped, review_uncapped_including_children) = own_scope.unwrap_or_else(|| {
        (
            this_node_uncapped.capped(&remaining),
            review_uncapped_including_children,
        )
    });

    node.new_count = this_node_capped.new;
    node.review_count = this_node_capped.review;
    node.learn_count = this_node_capped.intraday_learning + this_node_capped.interday_learning;
    node.total_including_children = total_including_children;
    node.review_uncapped_including_children = review_uncapped_including_children;

    this_node_capped
}

fn hide_default_deck(node: &mut DeckTreeNode) {
    for (idx, child) in node.children.iter().enumerate() {
        // we can hide the default if it has no children
        if child.deck_id == 1 && child.children.is_empty() {
            if child.level == 1 && node.children.len() == 1 {
                // can't remove if there are no other decks
            } else {
                // safe to remove
                _ = node.children.remove(idx);
            }
            return;
        }
    }
}

/// Locate provided deck in tree, and return it.
pub fn get_deck_in_tree(tree: DeckTreeNode, deck_id: DeckId) -> Option<DeckTreeNode> {
    if tree.deck_id == deck_id.0 {
        return Some(tree);
    }
    for child in tree.children {
        if let Some(node) = get_deck_in_tree(child, deck_id) {
            return Some(node);
        }
    }

    None
}

pub(crate) fn sum_deck_tree_node<T: AddAssign>(
    node: &DeckTreeNode,
    map: fn(&DeckTreeNode) -> T,
) -> T {
    let mut output = map(node);
    for child in &node.children {
        output += sum_deck_tree_node(child, map)
    }
    output
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
    /// - If `timestamp` is provided, due counts for the provided timestamp will
    ///   be populated.
    /// - Buried cards from previous days will be unburied if necessary. Because
    ///   this does not happen for future stamps, future due numbers may not be
    ///   accurate.
    pub fn deck_tree(&mut self, timestamp: Option<TimestampSecs>) -> Result<DeckTreeNode> {
        let names = self.storage.get_all_deck_names()?;
        let mut tree = deck_names_to_tree(names.into_iter());

        let decks_map = self.storage.get_decks_map()?;

        add_collapsed_and_filtered(&mut tree, &decks_map, timestamp.is_none());
        if self.default_deck_is_empty()? {
            hide_default_deck(&mut tree);
        }

        if let Some(timestamp) = timestamp {
            // cards buried on previous days need to be unburied for the current
            // day's counts to be accurate
            let timing_today = self.timing_today()?;
            self.unbury_if_day_rolled_over(timing_today)?;

            let timing_at_stamp = self.timing_for_timestamp(timestamp)?;
            let days_elapsed = timing_at_stamp.days_elapsed;
            let learn_cutoff = (timestamp.0 as u32) + self.learn_ahead_secs();
            let new_cards_ignore_review_limit =
                self.get_config_bool(BoolKey::NewCardsIgnoreReviewLimit);
            let parent_limits = self
                .get_config_bool(BoolKey::ApplyAllParentLimits)
                .then(Default::default);
            let dconf = self.storage.get_deck_config_map()?;
            let mut counts = self.due_counts(timing_at_stamp, learn_cutoff)?;
            for (deck_id, count) in &mut counts {
                let enabled = decks_map
                    .get(deck_id)
                    .and_then(Deck::config_id)
                    .and_then(|config_id| dconf.get(&config_id))
                    .is_some_and(|config| config.inner.same_day_reviews_ignore_review_limit);
                if !enabled {
                    count.review_limit_exempt = 0;
                    count.interday_learning_limit_exempt = 0;
                }
            }
            self.rwkv_offline_before_deck_counts();
            let scoped_counts =
                self.rwkv_review_queue_counts(&counts, &decks_map, &dconf, timing_at_stamp)?;
            add_counts(&mut tree, &counts);
            let limits = remaining_limits_map(
                decks_map.values(),
                &dconf,
                days_elapsed,
                new_cards_ignore_review_limit,
            );
            sum_counts_and_apply_limits_v3(
                &mut tree,
                &limits,
                &counts,
                &scoped_counts,
                parent_limits,
            );
        }

        Ok(tree)
    }

    pub fn current_deck_tree(&mut self) -> Result<Option<DeckTreeNode>> {
        let target = self.get_current_deck_id();
        let tree = self.deck_tree(Some(TimestampSecs::now()))?;
        Ok(get_deck_in_tree(tree, target))
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
        let tree = self.deck_tree(Some(TimestampSecs::now()))?;
        Ok(LegacyDueCounts::from(tree))
    }

    pub(crate) fn add_missing_deck_names(&mut self, names: &[(DeckId, String)]) -> Result<usize> {
        let mut parents = HashSet::new();
        let mut missing = 0;
        for (_id, name) in names {
            parents.insert(UniCase::new(name.as_str()));
            if let Some((immediate_parent, _)) = name.rsplit_once("::") {
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
    use std::collections::HashMap;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::deckconfig::DeckConfigId;
    use crate::deckconfig::ReviewCardOrder;
    use crate::error::Result;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::timing::SchedTimingToday;

    #[test]
    fn wellformed() -> Result<()> {
        let mut col = Collection::new();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2")?;
        col.get_or_create_normal_deck("2::a")?;
        col.get_or_create_normal_deck("2::b")?;
        col.get_or_create_normal_deck("2::c")?;
        col.get_or_create_normal_deck("2::c::A")?;
        col.get_or_create_normal_deck("3")?;

        let tree = col.deck_tree(None)?;

        assert_eq!(tree.children.len(), 3);

        assert_eq!(tree.children[1].name, "2");
        assert_eq!(tree.children[1].children[0].name, "a");
        assert_eq!(tree.children[1].children[2].name, "c");
        assert_eq!(tree.children[1].children[2].children[0].name, "A");

        Ok(())
    }

    #[test]
    fn malformed() -> Result<()> {
        let mut col = Collection::new();

        col.get_or_create_normal_deck("1")?;
        col.get_or_create_normal_deck("2::3::4")?;

        // remove the top parent and middle parent
        col.storage.remove_deck(col.get_deck_id("2")?.unwrap())?;
        col.storage.remove_deck(col.get_deck_id("2::3")?.unwrap())?;

        let tree = col.deck_tree(None)?;
        assert_eq!(tree.children.len(), 1);

        Ok(())
    }

    #[test]
    fn counts() -> Result<()> {
        let mut col = Collection::new();

        let mut parent_deck = col.get_or_create_normal_deck("Default")?;
        let mut child_deck = col.get_or_create_normal_deck("Default::one")?;

        // add some new cards
        let nt = col.get_notetype_by_name("Cloze")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "{{c1::}} {{c2::}} {{c3::}} {{c4::}}")?;
        col.add_note(&mut note, child_deck.id)?;

        let tree = col.deck_tree(Some(TimestampSecs::now()))?;
        assert_eq!(tree.children[0].new_count, 4);
        assert_eq!(tree.children[0].children[0].new_count, 4);

        // simulate answering a card
        child_deck.common.new_studied = 1;
        col.add_or_update_deck(&mut child_deck)?;
        parent_deck.common.new_studied = 1;
        col.add_or_update_deck(&mut parent_deck)?;

        // with the default limit of 20, there should still be 4 due
        let tree = col.deck_tree(Some(TimestampSecs::now()))?;
        assert_eq!(tree.children[0].new_count, 4);
        assert_eq!(tree.children[0].children[0].new_count, 4);

        // set the limit to 4, which should mean 3 are left
        let mut conf = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        conf.inner.new_per_day = 4;
        col.add_or_update_deck_config(&mut conf)?;

        let tree = col.deck_tree(Some(TimestampSecs::now()))?;
        assert_eq!(tree.children[0].new_count, 3);
        assert_eq!(tree.children[0].children[0].new_count, 3);

        Ok(())
    }

    #[test]
    fn nested_counts_v3() -> Result<()> {
        fn create_deck_with_new_limit(col: &mut Collection, name: &str, new_limit: u32) -> Deck {
            let mut deck = col.get_or_create_normal_deck(name).unwrap();
            let mut conf = DeckConfig::default();
            conf.inner.new_per_day = new_limit;
            col.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            col.add_or_update_deck(&mut deck).unwrap();
            deck
        }

        let mut col = Collection::new();

        let parent_deck = create_deck_with_new_limit(&mut col, "Default", 8);
        let child_deck = create_deck_with_new_limit(&mut col, "Default::child", 4);
        let grandchild_1 = create_deck_with_new_limit(&mut col, "Default::child::grandchild_1", 2);
        let grandchild_2 = create_deck_with_new_limit(&mut col, "Default::child::grandchild_2", 1);

        // add 2 new cards to each deck
        let nt = col.get_notetype_by_name("Cloze")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "{{c1::}} {{c2::}}")?;
        col.add_note(&mut note, parent_deck.id)?;
        note.id.0 = 0;
        col.add_note(&mut note, child_deck.id)?;
        note.id.0 = 0;
        col.add_note(&mut note, grandchild_1.id)?;
        note.id.0 = 0;
        col.add_note(&mut note, grandchild_2.id)?;

        let parent = &col.deck_tree(Some(TimestampSecs::now()))?.children[0];
        // grandchildren: own cards, limited by own new limits
        assert_eq!(parent.children[0].children[0].new_count, 2);
        assert_eq!(parent.children[0].children[1].new_count, 1);
        // child: cards from self and children, limited by own new limit
        assert_eq!(parent.children[0].new_count, 4);
        // parent: cards from self and all subdecks, all limits in the hierarchy are
        // respected
        assert_eq!(parent.new_count, 6);
        assert_eq!(parent.total_including_children, 8);
        assert_eq!(parent.total_in_deck, 2);

        Ok(())
    }

    fn add_review_card(
        col: &mut Collection,
        deck_id: DeckId,
        due: i32,
        desired_retention: f32,
        last_review_time: Option<TimestampSecs>,
    ) -> Result<CardId> {
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "foo")?;
        col.add_note(&mut note, deck_id)?;
        let mut card = col.storage.get_card_by_ordinal(note.id, 0)?.unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = due;
        card.interval = 1;
        card.desired_retention = Some(desired_retention);
        card.last_review_time = last_review_time;
        col.storage.update_card(&card)?;
        Ok(card.id)
    }

    fn add_review_log_today(
        col: &mut Collection,
        card_id: CardId,
        timing: SchedTimingToday,
    ) -> Result<()> {
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: timing.now.as_millis().into(),
                cid: card_id,
                button_chosen: 3,
                interval: 1,
                review_kind: RevlogReviewKind::Review,
                ..Default::default()
            },
            true,
        )?;
        Ok(())
    }

    fn enable_rwkv_review_counts(
        col: &mut Collection,
        deck: &mut Deck,
        allow_same_day_review: bool,
    ) -> Result<()> {
        let mut conf = DeckConfig::default();
        conf.inner.review_order = ReviewCardOrder::Day as i32;
        conf.inner.rwkv_review_enabled = true;
        conf.inner.rwkv_review_instant_order_enabled = true;
        col.set_config_bool(
            BoolKey::FsrsShortTermWithStepsEnabled,
            allow_same_day_review,
            false,
        )?;
        conf.inner.rwkv_review_min_intervening_reviews = 0;
        conf.inner.rwkv_review_min_elapsed_secs = 0;
        col.add_or_update_deck_config(&mut conf)?;
        deck.normal_mut().unwrap().config_id = conf.id.0;
        col.add_or_update_deck(deck)?;
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_exclude_ineligible_scored_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        enable_rwkv_review_counts(&mut col, &mut deck, false)?;
        let timing = col.timing_today()?;

        let high_r_due =
            add_review_card(&mut col, deck.id, timing.days_elapsed as i32, 0.75, None)?;
        add_review_card(&mut col, deck.id, timing.days_elapsed as i32, 0.75, None)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(high_r_due, 0.80)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 1);
        assert_eq!(tree.children[0].review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_include_limit_exempt_same_day_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        enable_rwkv_review_counts(&mut col, &mut deck, true)?;
        let config_id = deck.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.reviews_per_day = 0;
        config.inner.same_day_reviews_ignore_review_limit = true;
        col.add_or_update_deck_config(&mut config)?;
        let timing = col.timing_today()?;

        let same_day = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32 + 1,
            0.75,
            Some(timing.now),
        )?;
        add_review_log_today(&mut col, same_day, timing)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(same_day, 0.50)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 1);
        assert_eq!(tree.children[0].review_uncapped, 1);

        config.inner.same_day_reviews_ignore_review_limit = false;
        col.add_or_update_deck_config(&mut config)?;
        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 0);
        assert_eq!(tree.children[0].review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn deck_tree_counts_include_limit_exempt_same_day_reviews() -> Result<()> {
        let mut col = Collection::new();
        let deck = col.get_or_create_normal_deck("Default")?;
        let config_id = deck.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.reviews_per_day = 0;
        config.inner.same_day_reviews_ignore_review_limit = true;
        col.add_or_update_deck_config(&mut config)?;
        let timing = col.timing_today()?;

        let same_day = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32,
            0.75,
            Some(timing.now),
        )?;
        add_review_log_today(&mut col, same_day, timing)?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 1);
        assert_eq!(tree.children[0].review_uncapped, 1);

        config.inner.same_day_reviews_ignore_review_limit = false;
        col.add_or_update_deck_config(&mut config)?;
        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 0);
        assert_eq!(tree.children[0].review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_keep_filtered_deck_reviews_due() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = col.get_or_create_normal_deck("Parent")?;
        enable_rwkv_review_counts(&mut col, &mut parent, false)?;
        let timing = col.timing_today()?;

        let card_id = add_review_card(&mut col, parent.id, timing.days_elapsed as i32, 0.75, None)?;
        let mut filtered = Deck::new_filtered();
        filtered.name = NativeDeckName::from_human_name("Parent::Filtered");
        col.add_or_update_deck(&mut filtered)?;

        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.original_deck_id = card.deck_id;
        card.original_due = card.due;
        card.deck_id = filtered.id;
        card.due = -100_000;
        col.storage.update_card(&card)?;

        col.set_rwkv_deck_count_scores(parent.id, HashMap::from([(card_id, 0.80)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        let filtered = get_deck_in_tree(tree, filtered.id).unwrap();
        assert_eq!(filtered.review_count, 1);
        assert_eq!(filtered.review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_include_daily_minimum_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        enable_rwkv_review_counts(&mut col, &mut deck, false)?;
        let config_id = deck.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.rwkv_review_minimum_reviews_per_day = 2;
        col.add_or_update_deck_config(&mut config)?;
        let timing = col.timing_today()?;

        let first = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let second = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let third = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(first, 0.80), (second, 0.90), (third, 0.95)]),
        )?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 2);
        assert_eq!(tree.children[0].review_uncapped, 2);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_use_each_selected_deck_minimum() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = col.get_or_create_normal_deck("Parent")?;
        let mut lower = col.get_or_create_normal_deck("Parent::Lower")?;
        let mut higher = col.get_or_create_normal_deck("Parent::Higher")?;
        for deck in [&mut parent, &mut lower, &mut higher] {
            enable_rwkv_review_counts(&mut col, deck, false)?;
            let mut config = col
                .get_deck_config(deck.config_id().unwrap(), false)?
                .unwrap();
            config.inner.rwkv_review_minimum_reviews_per_day = 1;
            col.add_or_update_deck_config(&mut config)?;
        }
        let timing = col.timing_today()?;
        let lower_card = add_review_card(
            &mut col,
            lower.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let higher_card = add_review_card(
            &mut col,
            higher.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let scores = HashMap::from([(lower_card, 0.80), (higher_card, 0.95)]);
        for deck in [&parent, &lower, &higher] {
            col.set_rwkv_deck_count_scores(deck.id, scores.clone())?;
        }

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(
            get_deck_in_tree(tree.clone(), parent.id)
                .unwrap()
                .review_count,
            1
        );
        assert_eq!(
            get_deck_in_tree(tree.clone(), lower.id)
                .unwrap()
                .review_count,
            1
        );
        assert_eq!(get_deck_in_tree(tree, higher.id).unwrap().review_count, 1);

        parent.set_rwkv_respect_subdeck_minimums(true)?;
        col.add_or_update_deck(&mut parent)?;
        for deck in [&parent, &lower, &higher] {
            col.set_rwkv_deck_count_scores(deck.id, scores.clone())?;
        }
        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(get_deck_in_tree(tree, parent.id).unwrap().review_count, 2);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_minimum_ignores_reviews_blocked_by_subdeck_limit() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = col.get_or_create_normal_deck("Parent")?;
        let mut blocked = col.get_or_create_normal_deck("Parent::Blocked")?;
        let available = col.get_or_create_normal_deck("Parent::Available")?;
        enable_rwkv_review_counts(&mut col, &mut parent, false)?;
        let mut config = col
            .get_deck_config(parent.config_id().unwrap(), false)?
            .unwrap();
        config.inner.rwkv_review_minimum_reviews_per_day = 1;
        col.add_or_update_deck_config(&mut config)?;
        blocked.normal_mut()?.review_limit = Some(0);
        col.add_or_update_deck(&mut blocked)?;

        let timing = col.timing_today()?;
        let blocked_card =
            add_review_card(&mut col, blocked.id, timing.days_elapsed as i32, 0.75, None)?;
        let available_card = add_review_card(
            &mut col,
            available.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let scores = HashMap::from([(blocked_card, 0.65), (available_card, 0.95)]);
        col.set_rwkv_review_queue_scores(parent.id, scores.clone())?;
        assert_eq!(col.build_queues(parent.id)?.counts().review, 1);
        col.set_rwkv_deck_count_scores(parent.id, scores)?;
        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(get_deck_in_tree(tree, parent.id).unwrap().review_count, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_browser_counts_use_selected_minimum_with_parent_limits() -> Result<()> {
        for (apply_parent_limits, studied, expected_child_count) in
            [(true, 3, 3), (false, 3, 3), (true, 25, 0)]
        {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::ApplyAllParentLimits, apply_parent_limits, false)?;
            let mut parent = col.get_or_create_normal_deck("All")?;
            let mut child = col.get_or_create_normal_deck("All::Reading")?;
            let mut leaf = col.get_or_create_normal_deck("All::Reading::Words")?;
            let timing = col.timing_today()?;
            for (deck, minimum, daily_limit) in [
                (&mut parent, 25, 20),
                (&mut child, 3, 9999),
                (&mut leaf, 0, 9999),
            ] {
                enable_rwkv_review_counts(&mut col, deck, false)?;
                let mut config = col
                    .get_deck_config(deck.config_id().unwrap(), false)?
                    .unwrap();
                config.inner.rwkv_review_minimum_reviews_per_day = minimum;
                config.inner.reviews_per_day = daily_limit;
                col.add_or_update_deck_config(&mut config)?;
            }
            parent.common.last_day_studied = timing.days_elapsed;
            parent.common.review_studied = studied;
            col.add_or_update_deck(&mut parent)?;

            let mut scores = HashMap::new();
            for _ in 0..22 {
                let card = add_review_card(
                    &mut col,
                    leaf.id,
                    timing.days_elapsed as i32 + 7,
                    0.75,
                    None,
                )?;
                scores.insert(card, 0.80);
            }
            for deck in [&parent, &child, &leaf] {
                col.set_rwkv_deck_count_scores(deck.id, scores.clone())?;
            }
            let tree = col.deck_tree(Some(timing.now))?;
            for (deck, expected_count, expected_uncapped) in
                [(&child, expected_child_count, 3), (&leaf, 0, 0)]
            {
                col.set_rwkv_review_queue_scores(deck.id, scores.clone())?;
                assert_eq!(
                    col.build_queues(deck.id)?.counts().review,
                    expected_count as usize
                );
                let counts = get_deck_in_tree(tree.clone(), deck.id).unwrap();
                assert_eq!(counts.review_count, expected_count);
                assert_eq!(counts.review_uncapped_including_children, expected_uncapped);
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_minimum_counts_filtered_reviews_once_at_source() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = col.get_or_create_normal_deck("Parent")?;
        let source = col.get_or_create_normal_deck("Parent::Source")?;
        enable_rwkv_review_counts(&mut col, &mut parent, false)?;
        let config_id = parent.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.rwkv_review_minimum_reviews_per_day = 2;
        col.add_or_update_deck_config(&mut config)?;

        let mut filtered = Deck::new_filtered();
        filtered.name = NativeDeckName::from_human_name("Parent::Filtered");
        col.add_or_update_deck(&mut filtered)?;

        let timing = col.timing_today()?;
        let moved = add_review_card(
            &mut col,
            source.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let mut moved_card = col.storage.get_card(moved)?.unwrap();
        moved_card.original_deck_id = moved_card.deck_id;
        moved_card.original_due = moved_card.due;
        moved_card.deck_id = filtered.id;
        moved_card.due = -100_000;
        col.storage.update_card(&moved_card)?;

        let first_pull = add_review_card(
            &mut col,
            source.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        let second_pull = add_review_card(
            &mut col,
            source.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        col.set_rwkv_review_queue_scores(
            parent.id,
            HashMap::from([(first_pull, 0.80), (second_pull, 0.90)]),
        )?;

        let tree = col.deck_tree(Some(timing.now))?;
        let parent_node = get_deck_in_tree(tree.clone(), parent.id).unwrap();
        let source_node = get_deck_in_tree(tree.clone(), source.id).unwrap();
        let filtered_node = get_deck_in_tree(tree, filtered.id).unwrap();
        assert_eq!(parent_node.review_count, 2);
        assert_eq!(source_node.review_count, 1);
        assert_eq!(filtered_node.review_count, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_include_future_eligible_scored_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        enable_rwkv_review_counts(&mut col, &mut deck, false)?;
        let timing = col.timing_today()?;

        let future_low_r = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(future_low_r, 0.20)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 1);
        assert_eq!(tree.children[0].review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_follow_same_day_toggle() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        let timing = col.timing_today()?;

        let reviewed_today = add_review_card(
            &mut col,
            deck.id,
            timing.days_elapsed as i32,
            0.75,
            Some(timing.now),
        )?;
        enable_rwkv_review_counts(&mut col, &mut deck, false)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(reviewed_today, 0.20)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 0);
        assert_eq!(tree.children[0].review_uncapped, 0);

        enable_rwkv_review_counts(&mut col, &mut deck, true)?;
        let tree = col.deck_tree(Some(timing.now))?;
        assert_eq!(tree.children[0].review_count, 1);
        assert_eq!(tree.children[0].review_uncapped, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_browser_counts_match_each_decks_review_queue() -> Result<()> {
        use crate::collection::RwkvReviewQueueScoreEntry;

        for (parent_intervening, child_intervening, expected_parent, expected_child) in
            [(4, 2, 0, 1), (6, 1, 1, 0), (6, 2, 1, 1)]
        {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::ApplyAllParentLimits, true, false)?;
            let mut parent = col.get_or_create_normal_deck("All")?;
            let mut child = col.get_or_create_normal_deck("All::Reading")?;
            let timing = col.timing_today()?;
            for (deck, minimum, daily_limit) in [(&mut parent, 6, 50), (&mut child, 2, 9999)] {
                enable_rwkv_review_counts(&mut col, deck, true)?;
                let mut config = col
                    .get_deck_config(deck.config_id().unwrap(), false)?
                    .unwrap();
                config.inner.rwkv_review_min_intervening_reviews = minimum;
                config.inner.rwkv_review_min_elapsed_secs = 0;
                config.inner.reviews_per_day = daily_limit;
                config.inner.same_day_reviews_ignore_review_limit = true;
                col.add_or_update_deck_config(&mut config)?;
            }
            parent.common.last_day_studied = timing.days_elapsed;
            parent.common.review_studied = 50;
            col.add_or_update_deck(&mut parent)?;
            let mut leaf = col.get_or_create_normal_deck("All::Reading::Words")?;
            leaf.normal_mut().unwrap().config_id = child.config_id().unwrap().0;
            col.add_or_update_deck(&mut leaf)?;
            let card = add_review_card(
                &mut col,
                leaf.id,
                timing.days_elapsed as i32 + 1,
                0.75,
                Some(timing.now),
            )?;
            add_review_log_today(&mut col, card, timing)?;
            let scores = |intervening_reviews| {
                HashMap::from([(
                    card,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.50,
                        intervening_reviews: Some(intervening_reviews),
                        target_retention: Some(0.75),
                    },
                )])
            };

            col.set_rwkv_deck_count_score_entries(parent.id, scores(parent_intervening))?;
            col.set_rwkv_deck_count_score_entries(child.id, scores(child_intervening))?;
            col.set_rwkv_deck_count_score_entries(leaf.id, scores(child_intervening))?;
            let tree = col.deck_tree(Some(timing.now))?;
            let parent_counts = get_deck_in_tree(tree, parent.id).unwrap();
            assert_eq!(parent_counts.review_count, expected_parent);
            assert_eq!(parent_counts.children[0].review_count, expected_child);
            assert_eq!(
                parent_counts.children[0].children[0].review_count,
                expected_child
            );
            assert_eq!(
                parent_counts.review_uncapped_including_children,
                expected_parent
            );
            assert_eq!(
                parent_counts.children[0].review_uncapped_including_children,
                expected_child
            );

            for (deck, intervening, expected) in [
                (&parent, parent_intervening, expected_parent),
                (&child, child_intervening, expected_child),
            ] {
                col.set_rwkv_review_queue_score_entries(deck.id, scores(intervening))?;
                let overview = get_deck_in_tree(col.deck_tree(Some(timing.now))?, deck.id).unwrap();
                assert_eq!(overview.review_count, expected);
                assert_eq!(
                    col.build_queues(deck.id)?.counts().review,
                    expected as usize
                );
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_accumulate_disjoint_deck_browser_scopes() -> Result<()> {
        let mut col = Collection::new();
        let mut first_deck = col.get_or_create_normal_deck("First")?;
        let mut second_deck = col.get_or_create_normal_deck("Second")?;
        enable_rwkv_review_counts(&mut col, &mut first_deck, false)?;
        enable_rwkv_review_counts(&mut col, &mut second_deck, false)?;
        let timing = col.timing_today()?;

        let first_high_r = add_review_card(
            &mut col,
            first_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;
        add_review_card(
            &mut col,
            first_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;
        let second_high_r = add_review_card(
            &mut col,
            second_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;
        add_review_card(
            &mut col,
            second_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;

        col.set_rwkv_deck_count_scores(first_deck.id, HashMap::from([(first_high_r, 0.80)]))?;
        col.set_rwkv_deck_count_scores(second_deck.id, HashMap::from([(second_high_r, 0.80)]))?;

        let tree = col.deck_tree(Some(timing.now))?;
        let first = get_deck_in_tree(tree.clone(), first_deck.id).unwrap();
        let second = get_deck_in_tree(tree, second_deck.id).unwrap();
        assert_eq!(first.review_count, 1);
        assert_eq!(second.review_count, 1);

        col.clear_rwkv_deck_count_scores();
        let tree = col.deck_tree(Some(timing.now))?;
        let first = get_deck_in_tree(tree.clone(), first_deck.id).unwrap();
        let second = get_deck_in_tree(tree, second_deck.id).unwrap();
        assert_eq!(first.review_count, 2);
        assert_eq!(second.review_count, 2);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_ignore_scores_outside_the_declared_scope() -> Result<()> {
        let mut col = Collection::new();
        let mut first_deck = col.get_or_create_normal_deck("First")?;
        let mut second_deck = col.get_or_create_normal_deck("Second")?;
        enable_rwkv_review_counts(&mut col, &mut first_deck, false)?;
        enable_rwkv_review_counts(&mut col, &mut second_deck, false)?;
        let timing = col.timing_today()?;

        let second_due = add_review_card(
            &mut col,
            second_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;
        let second_future = add_review_card(
            &mut col,
            second_deck.id,
            timing.days_elapsed as i32 + 7,
            0.75,
            None,
        )?;

        col.set_rwkv_review_queue_scores(first_deck.id, HashMap::from([(second_future, 0.20)]))?;
        let tree = col.deck_tree(Some(timing.now))?;
        let second = get_deck_in_tree(tree, second_deck.id).unwrap();
        assert_eq!(second.review_count, 1);

        col.set_rwkv_review_queue_scores(first_deck.id, HashMap::from([(second_due, 0.80)]))?;
        let tree = col.deck_tree(Some(timing.now))?;
        let second = get_deck_in_tree(tree, second_deck.id).unwrap();
        assert_eq!(second.review_count, 1);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_ignore_stale_scores_after_filtered_deck_rebuild() -> Result<()> {
        let mut col = Collection::new();
        let mut home_deck = col.get_or_create_normal_deck("Home")?;
        enable_rwkv_review_counts(&mut col, &mut home_deck, false)?;
        let timing = col.timing_today()?;
        let card_id = add_review_card(
            &mut col,
            home_deck.id,
            timing.days_elapsed as i32,
            0.75,
            None,
        )?;
        col.set_rwkv_review_queue_scores(home_deck.id, HashMap::from([(card_id, 0.80)]))?;

        let mut filtered = col.get_or_create_filtered_deck(DeckId(0))?;
        filtered.allow_empty = false;
        filtered.config.search_terms[0].search = "deck:Home is:review".into();
        filtered.config.search_terms[0].limit = 1;
        filtered.config.search_terms[1].limit = 0;
        let filtered_deck_id = col.add_or_update_filtered_deck(filtered)?.output;

        col.set_current_deck(filtered_deck_id)?;
        assert!(col.current_deck_tree()?.is_some());
        col.rebuild_filtered_deck(filtered_deck_id)?;
        assert!(col.current_deck_tree()?.is_some());

        col.set_current_deck(home_deck.id)?;
        let home = col.current_deck_tree()?.unwrap();
        assert_eq!(home.review_count, 0);
        Ok(())
    }

    #[test]
    fn rwkv_deck_tree_counts_tolerate_missing_intermediate_parent() -> Result<()> {
        let mut col = Collection::new();
        let mut root = col.get_or_create_normal_deck("Root")?;
        let missing_parent = col.get_or_create_normal_deck("Root::Missing")?;
        let leaf = col.get_or_create_normal_deck("Root::Missing::Leaf")?;
        enable_rwkv_review_counts(&mut col, &mut root, false)?;
        let timing = col.timing_today()?;
        let card_id = add_review_card(&mut col, leaf.id, timing.days_elapsed as i32, 0.75, None)?;
        col.set_rwkv_review_queue_scores(root.id, HashMap::from([(card_id, 0.80)]))?;
        col.storage.remove_deck(missing_parent.id)?;

        let _ = col.deck_tree(Some(timing.now))?;
        Ok(())
    }
}
