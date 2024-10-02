// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::bury_or_suspend_cards_request::Mode as BuryOrSuspendMode;
use anki_proto::scheduler::unbury_deck_request::Mode as UnburyDeckMode;

use super::queue::BuryMode;
use super::timing::SchedTimingToday;
use crate::card::CardQueue;
use crate::config::SchedulerVersion;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::SearchNode;
use crate::search::StateKind;

impl Card {
    /// True if card was buried/suspended prior to the call.
    pub(crate) fn restore_queue_after_bury_or_suspend(&mut self) -> bool {
        if !matches!(
            self.queue,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried
        ) {
            false
        } else {
            self.restore_queue_from_type();
            true
        }
    }
}

impl Collection {
    pub(crate) fn unbury_if_day_rolled_over(&mut self, timing: SchedTimingToday) -> Result<()> {
        let last_unburied = self.get_last_unburied_day();
        let today = timing.days_elapsed;
        if last_unburied < today || (today + 7) < last_unburied {
            self.unbury_on_day_rollover(today)?;
        }

        Ok(())
    }

    /// Unbury cards from the previous day.
    /// Done automatically, and does not mark the cards as modified.
    pub(crate) fn unbury_on_day_rollover(&mut self, today: u32) -> Result<()> {
        self.for_each_card_in_search(StateKind::Buried, |col, mut card| {
            card.restore_queue_after_bury_or_suspend();
            col.storage.update_card(&card)
        })?;
        self.set_last_unburied_day(today)
    }

    /// Unsuspend/unbury cards. Marks the cards as modified.
    fn unsuspend_or_unbury_searched_cards(&mut self, cards: Vec<Card>) -> Result<()> {
        let usn = self.usn()?;
        for original in cards {
            let mut card = original.clone();
            if card.restore_queue_after_bury_or_suspend() {
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }

    pub fn unbury_or_unsuspend_cards(&mut self, cids: &[CardId]) -> Result<OpOutput<()>> {
        self.transact(Op::UnburyUnsuspend, |col| {
            let cards = col.all_cards_for_ids(cids, false)?;
            col.unsuspend_or_unbury_searched_cards(cards)
        })
    }

    pub fn unbury_deck(&mut self, deck_id: DeckId, mode: UnburyDeckMode) -> Result<OpOutput<()>> {
        let state = match mode {
            UnburyDeckMode::All => StateKind::Buried,
            UnburyDeckMode::UserOnly => StateKind::UserBuried,
            UnburyDeckMode::SchedOnly => StateKind::SchedBuried,
        };
        self.transact(Op::UnburyUnsuspend, |col| {
            let cards =
                col.all_cards_for_search(SearchNode::DeckIdWithChildren(deck_id).and(state))?;
            col.unsuspend_or_unbury_searched_cards(cards)
        })
    }

    /// Marks the cards as modified.
    fn bury_or_suspend_cards_inner(
        &mut self,
        cards: Vec<Card>,
        mode: BuryOrSuspendMode,
    ) -> Result<usize> {
        let mut count = 0;
        let usn = self.usn()?;
        let sched = self.scheduler_version();
        if sched == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }
        let desired_queue = match mode {
            BuryOrSuspendMode::Suspend => CardQueue::Suspended,
            BuryOrSuspendMode::BurySched => CardQueue::SchedBuried,
            BuryOrSuspendMode::BuryUser => CardQueue::UserBuried,
        };

        for original in cards {
            let mut card = original.clone();
            if card.queue != desired_queue {
                // do not bury suspended cards as that would unsuspend them
                if card.queue != CardQueue::Suspended {
                    card.queue = desired_queue;
                    count += 1;
                    self.update_card_inner(&mut card, original, usn)?;
                }
            }
        }

        Ok(count)
    }

    pub fn bury_or_suspend_cards(
        &mut self,
        cids: &[CardId],
        mode: BuryOrSuspendMode,
    ) -> Result<OpOutput<usize>> {
        let op = match mode {
            BuryOrSuspendMode::Suspend => Op::Suspend,
            BuryOrSuspendMode::BurySched | BuryOrSuspendMode::BuryUser => Op::Bury,
        };
        self.transact(op, |col| {
            let cards = col.all_cards_for_ids(cids, false)?;
            col.bury_or_suspend_cards_inner(cards, mode)
        })
    }

    pub(crate) fn bury_siblings(
        &mut self,
        card: &Card,
        nid: NoteId,
        mut bury_mode: BuryMode,
    ) -> Result<usize> {
        bury_mode.exclude_earlier_gathered_queues(card.queue);
        let cards = self
            .storage
            .all_siblings_for_bury(card.id, nid, bury_mode)?;
        self.bury_or_suspend_cards_inner(cards, BuryOrSuspendMode::BurySched)
    }
}

impl BuryMode {
    /// Disables burying for queues gathered before `queue`.
    fn exclude_earlier_gathered_queues(&mut self, queue: CardQueue) {
        self.bury_interday_learning &= queue.gather_ord() <= CardQueue::DayLearn.gather_ord();
        self.bury_reviews &= queue.gather_ord() <= CardQueue::Review.gather_ord();
    }
}

impl CardQueue {
    fn gather_ord(self) -> u8 {
        match self {
            CardQueue::Learn | CardQueue::PreviewRepeat => 0,
            CardQueue::DayLearn => 1,
            CardQueue::Review => 2,
            CardQueue::New => 3,
            // not gathered
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => u8::MAX,
        }
    }
}

#[cfg(test)]
mod test {
    use crate::card::Card;
    use crate::card::CardQueue;
    use crate::collection::Collection;
    use crate::search::SortMode;
    use crate::search::StateKind;

    #[test]
    fn unbury() {
        let mut col = Collection::new();
        let mut card = Card {
            queue: CardQueue::UserBuried,
            ..Default::default()
        };
        col.add_card(&mut card).unwrap();
        let assert_count = |col: &mut Collection, cnt| {
            assert_eq!(
                col.search_cards(StateKind::Buried, SortMode::NoOrder)
                    .unwrap()
                    .len(),
                cnt
            );
        };
        assert_count(&mut col, 1);
        // day 0, last unburied 0, so no change
        let timing = col.timing_today().unwrap();
        col.unbury_if_day_rolled_over(timing).unwrap();
        assert_count(&mut col, 1);
        // move creation time back and it should succeed
        let mut stamp = col.storage.creation_stamp().unwrap();
        stamp.0 -= 86_400;
        col.set_creation_stamp(stamp).unwrap();
        let timing = col.timing_today().unwrap();
        col.unbury_if_day_rolled_over(timing).unwrap();
        assert_count(&mut col, 0);
    }
}
