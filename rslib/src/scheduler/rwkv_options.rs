// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! RWKV settings that apply outside of time budgets.
//!
//! With [RwkvOptions::all_decks], a deck that no time budget covers is
//! studied weakest-first as well: Anki's own queue still decides which
//! cards are due today, and the deck's daily limits, burying and new card
//! order all apply as usual, but the review and interday learning cards it
//! gathered are then reordered by predicted recall, lowest first. New cards
//! keep their places in the queue, so the deck's new/review mix is
//! unchanged.
//!
//! Decks with a time budget are studied by
//! [Collection::get_time_budget_queued_cards] instead, which also pulls
//! cards forward and stops on the day's time; this only sorts what would
//! have been shown anyway.

use std::collections::HashMap;

use serde::Deserialize;
use serde::Serialize;

use super::queue::CardQueues;
use crate::prelude::*;

/// Collection config key holding [RwkvOptions].
pub(crate) const RWKV_OPTIONS_KEY: &str = "rwkvOptions";

#[derive(Serialize, Deserialize, Debug, Clone, Copy, Default, PartialEq, Eq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct RwkvOptions {
    /// Order every deck's queue by predicted recall, not only the decks
    /// with a time budget.
    pub all_decks: bool,
}

impl Collection {
    pub(crate) fn rwkv_options(&self) -> RwkvOptions {
        self.get_config_optional(RWKV_OPTIONS_KEY)
            .unwrap_or_default()
    }

    /// Whether a freshly built queue for `deck_id` should be ordered by
    /// predicted recall. Decks with a time budget have their own queue, and
    /// a filtered deck keeps the order its search asked for.
    pub(crate) fn order_queue_by_recall(&mut self, deck_id: DeckId) -> Result<bool> {
        if !self.rwkv_options().all_decks {
            return Ok(false);
        }
        let Some(deck) = self.storage.get_deck(deck_id)? else {
            return Ok(false);
        };
        Ok(!deck.is_filtered() && self.active_time_budget(deck_id)?.is_none())
    }

    /// Reorders the queue's reviews by predicted recall, lowest first.
    pub(crate) fn sort_queue_by_recall(&mut self, queues: &mut CardQueues) -> Result<()> {
        let ids = queues.reorderable_ids();
        if ids.len() < 2 {
            return Ok(());
        }
        let cards = self.all_cards_for_ids(&ids, false)?;
        let recall = match self.predict_recall(&cards) {
            Ok(recall) => recall,
            // Studying in the normal order is better than not studying at
            // all, so a missing or broken model only logs.
            Err(err) => {
                tracing::warn!(?err, "could not order the queue by predicted recall");
                return Ok(());
            }
        };
        let scores: HashMap<CardId, f32> = cards.iter().map(|card| card.id).zip(recall).collect();
        queues.sort_reorderable_by(&scores);
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use anki_proto::deck_config::deck_config::config::ReviewCardOrder;

    use super::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::tests::CardAdder;
    use crate::tests::DeckAdder;

    const DAY_MILLIS: i64 = 86_400_000;

    /// A review card in `deck` that came due `due_days_ago` days ago, with
    /// answers (days ago, button, kind) in its review log.
    fn review_card(
        col: &mut Collection,
        deck: DeckId,
        due_days_ago: i32,
        answers: &[(i64, u8, RevlogReviewKind)],
    ) -> CardId {
        let card = CardAdder::new()
            .deck(deck)
            .due_dates(["0"])
            .add(col)
            .remove(0);
        // set_due_date only takes days from today, so backdate by hand; the
        // modification time is left alone, as the built queue checks it
        let today = col.timing_today().unwrap().days_elapsed as i64;
        col.storage
            .db
            .execute(
                "update cards set due = ? where id = ?",
                [today - due_days_ago as i64, card.id.0],
            )
            .unwrap();
        // revlog ids must not collide: a collision moves the id to now
        static NEXT_OFFSET: std::sync::atomic::AtomicI64 = std::sync::atomic::AtomicI64::new(0);
        let now = TimestampMillis::now().0;
        for (days_ago, button, kind) in answers {
            let offset = NEXT_OFFSET.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            col.storage
                .add_revlog_entry(
                    &RevlogEntry {
                        id: RevlogId(now - days_ago * DAY_MILLIS - 60_000 - offset),
                        cid: card.id,
                        button_chosen: *button,
                        interval: 1,
                        ease_factor: 2500,
                        taken_millis: 5_000,
                        review_kind: *kind,
                        ..Default::default()
                    },
                    false,
                )
                .unwrap();
        }
        card.id
    }

    fn queued_ids(col: &mut Collection) -> Vec<CardId> {
        col.get_queued_cards(100, false)
            .unwrap()
            .cards
            .into_iter()
            .map(|queued| queued.card.id)
            .collect()
    }

    fn set_all_decks(col: &mut Collection, all_decks: bool) {
        col.set_config(RWKV_OPTIONS_KEY, &RwkvOptions { all_decks })
            .unwrap();
    }

    #[test]
    fn all_decks_orders_the_normal_queue_by_recall() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Plain")
            .with_config(|config| config.inner.review_order = ReviewCardOrder::Day as i32)
            .add(&mut col);
        // the stronger card is the more overdue one, so the due order and
        // the recall order differ
        let strong = review_card(
            &mut col,
            deck.id,
            2,
            &[
                (90, 3, Learning),
                (80, 3, Review),
                (60, 4, Review),
                (1, 4, Review),
            ],
        );
        let weak = review_card(
            &mut col,
            deck.id,
            0,
            &[
                (40, 1, Learning),
                (40, 1, Learning),
                (40, 3, Learning),
                (30, 1, Review),
                (20, 1, Review),
                (6, 1, Review),
            ],
        );
        col.set_current_deck(deck.id)?;

        // no RWKV without the option: due date first
        assert_eq!(queued_ids(&mut col), vec![strong, weak]);

        // the option is picked up without the queue being cleared by hand,
        // so it also applies to a collection that synced it in
        set_all_decks(&mut col, true);
        let queue = col.get_queued_cards(100, false)?;
        let ids: Vec<_> = queue.cards.iter().map(|queued| queued.card.id).collect();
        assert_eq!(ids, vec![weak, strong]);
        // only the order changes: the deck's normal counts still apply
        assert_eq!(queue.review_count, 2);

        // answering works as usual, from the front of the reordered queue
        col.answer_good();
        assert_eq!(queued_ids(&mut col), vec![strong]);
        col.undo()?;
        assert_eq!(queued_ids(&mut col), vec![weak, strong]);

        set_all_decks(&mut col, false);
        assert_eq!(queued_ids(&mut col), vec![strong, weak]);
        Ok(())
    }

    #[test]
    fn new_cards_keep_their_place_in_the_queue() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Mixed")
            .with_config(|config| {
                config.inner.review_order = ReviewCardOrder::Day as i32;
                config.inner.new_per_day = 1;
            })
            .add(&mut col);
        let strong = review_card(
            &mut col,
            deck.id,
            2,
            &[(90, 3, Learning), (60, 4, Review), (1, 4, Review)],
        );
        let weak = review_card(
            &mut col,
            deck.id,
            0,
            &[(40, 1, Learning), (20, 1, Review), (6, 1, Review)],
        );
        let new_card = CardAdder::new().deck(deck.id).add(&mut col)[0].id;
        col.set_current_deck(deck.id)?;
        let without = queued_ids(&mut col);
        assert_eq!(without.len(), 3);
        let new_position = without.iter().position(|id| *id == new_card).unwrap();

        set_all_decks(&mut col, true);
        let with = queued_ids(&mut col);
        // the new card is where the deck's new/review mix put it, and the
        // reviews around it are weakest first
        assert_eq!(
            with.iter().position(|id| *id == new_card),
            Some(new_position)
        );
        let reviews: Vec<_> = with.into_iter().filter(|id| *id != new_card).collect();
        assert_eq!(reviews, vec![weak, strong]);
        Ok(())
    }

    #[test]
    fn budget_decks_keep_their_own_queue() -> Result<()> {
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        col.set_current_deck(deck.id)?;
        set_all_decks(&mut col, true);
        assert!(col.order_queue_by_recall(deck.id)?);

        let config: crate::scheduler::time_budget::TimeBudgetConfig = serde_json::from_str(
            &format!(r#"{{"decks": {{"{}": {{"minutes": 20}}}}}}"#, deck.id.0),
        )?;
        col.set_config(
            crate::scheduler::time_budget::TIME_BUDGET_CONFIG_KEY,
            &config,
        )?;
        // the time budget's own queue pulls cards forward and stops on the
        // day's time, so it is not reordered on top of
        assert!(!col.order_queue_by_recall(deck.id)?);
        Ok(())
    }
}
