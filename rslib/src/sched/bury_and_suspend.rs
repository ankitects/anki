// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    err::Result,
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
                    let original_due = if self.odue > 0 { self.odue } else { self.due };
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
        self.search_cards_into_table("is:buried")?;
        self.storage.for_each_card_in_search(|mut card| {
            card.restore_queue_after_bury_or_suspend();
            self.storage.update_card(&card)
        })?;
        self.clear_searched_cards()
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
        self.clear_searched_cards()
    }

    pub fn unbury_or_unsuspend_cards(&mut self, cids: &[CardID]) -> Result<()> {
        self.transact(None, |col| {
            col.set_search_table_to_card_ids(cids)?;
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
            col.search_cards_into_table(&format!("deck:current {}", search))?;
            col.unsuspend_or_unbury_searched_cards()
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
