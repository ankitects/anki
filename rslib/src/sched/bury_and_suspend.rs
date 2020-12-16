// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    config::SchedulerVersion,
    err::Result,
    search::SortMode,
};

use super::cutoff::SchedTimingToday;
use pb::unbury_cards_in_current_deck_in::Mode as UnburyDeckMode;

impl Card {
    /// True if card was buried/suspended prior to the call.
    pub(crate) fn restore_queue_after_bury_or_suspend(&mut self) -> bool {
        if !matches!(
            self.queue,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried
        ) {
            false
        } else {
            self.queue = match self.ctype {
                CardType::Learn | CardType::Relearn => {
                    let original_due = if self.original_due > 0 {
                        self.original_due
                    } else {
                        self.due
                    };
                    if original_due > 1_000_000_000 {
                        // previous interval was in seconds
                        CardQueue::Learn
                    } else {
                        // previous interval was in days
                        CardQueue::DayLearn
                    }
                }
                CardType::New => CardQueue::New,
                CardType::Review => CardQueue::Review,
            };
            true
        }
    }
}

impl Collection {
    pub(crate) fn unbury_if_day_rolled_over(&mut self, timing: SchedTimingToday) -> Result<()> {
        let last_unburied = self.get_last_unburied_day();
        let today = timing.days_elapsed;
        if last_unburied < today || (today + 7) < last_unburied {
            self.unbury_on_day_rollover()?;
            self.set_last_unburied_day(today)?;
        }

        Ok(())
    }

    /// Unbury cards from the previous day.
    /// Done automatically, and does not mark the cards as modified.
    fn unbury_on_day_rollover(&mut self) -> Result<()> {
        self.search_cards_into_table("is:buried", SortMode::NoOrder)?;
        self.storage.for_each_card_in_search(|mut card| {
            card.restore_queue_after_bury_or_suspend();
            self.storage.update_card(&card)
        })?;
        self.storage.clear_searched_cards_table()
    }

    /// Unsuspend/unbury cards in search table, and clear it.
    /// Marks the cards as modified.
    fn unsuspend_or_unbury_searched_cards(&mut self) -> Result<()> {
        let usn = self.usn()?;
        for original in self.storage.all_searched_cards()? {
            let mut card = original.clone();
            if card.restore_queue_after_bury_or_suspend() {
                self.update_card(&mut card, &original, usn)?;
            }
        }
        self.storage.clear_searched_cards_table()
    }

    pub fn unbury_or_unsuspend_cards(&mut self, cids: &[CardID]) -> Result<()> {
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, false)?;
            col.unsuspend_or_unbury_searched_cards()
        })
    }

    pub fn unbury_cards_in_current_deck(&mut self, mode: UnburyDeckMode) -> Result<()> {
        let search = match mode {
            UnburyDeckMode::All => "is:buried",
            UnburyDeckMode::UserOnly => "is:buried-manually",
            UnburyDeckMode::SchedOnly => "is:buried-sibling",
        };
        self.transact(None, |col| {
            col.search_cards_into_table(&format!("deck:current {}", search), SortMode::NoOrder)?;
            col.unsuspend_or_unbury_searched_cards()
        })
    }

    /// Bury/suspend cards in search table, and clear it.
    /// Marks the cards as modified.
    fn bury_or_suspend_searched_cards(
        &mut self,
        mode: pb::bury_or_suspend_cards_in::Mode,
    ) -> Result<()> {
        use pb::bury_or_suspend_cards_in::Mode;
        let usn = self.usn()?;
        let sched = self.sched_ver();

        for original in self.storage.all_searched_cards()? {
            let mut card = original.clone();
            let desired_queue = match mode {
                Mode::Suspend => CardQueue::Suspended,
                Mode::BurySched => CardQueue::SchedBuried,
                Mode::BuryUser => {
                    if sched == SchedulerVersion::V1 {
                        // v1 scheduler only had one bury type
                        CardQueue::SchedBuried
                    } else {
                        CardQueue::UserBuried
                    }
                }
            };
            if card.queue != desired_queue {
                if sched == SchedulerVersion::V1 {
                    card.remove_from_filtered_deck_restoring_queue(sched);
                    card.remove_from_learning();
                }
                card.queue = desired_queue;
                self.update_card(&mut card, &original, usn)?;
            }
        }

        self.storage.clear_searched_cards_table()
    }

    pub fn bury_or_suspend_cards(
        &mut self,
        cids: &[CardID],
        mode: pb::bury_or_suspend_cards_in::Mode,
    ) -> Result<()> {
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, false)?;
            col.bury_or_suspend_searched_cards(mode)
        })
    }
}

#[cfg(test)]
mod test {
    use crate::{
        card::{Card, CardQueue},
        collection::{open_test_collection, Collection},
        search::SortMode,
    };

    #[test]
    fn unbury() {
        let mut col = open_test_collection();
        let mut card = Card::default();
        card.queue = CardQueue::UserBuried;
        col.add_card(&mut card).unwrap();
        let assert_count = |col: &mut Collection, cnt| {
            assert_eq!(
                col.search_cards("is:buried", SortMode::NoOrder)
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
        col.storage.set_creation_stamp(stamp).unwrap();
        let timing = col.timing_today().unwrap();
        col.unbury_if_day_rolled_over(timing).unwrap();
        assert_count(&mut col, 0);
    }
}
