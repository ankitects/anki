// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use super::DeckCommon;
use crate::prelude::*;

impl Deck {
    pub(super) fn reset_stats_if_day_changed(&mut self, today: u32) {
        let c = &mut self.common;
        if c.last_day_studied != today {
            c.new_studied = 0;
            c.learning_studied = 0;
            c.review_studied = 0;
            c.milliseconds_studied = 0;
            c.last_day_studied = today;
        }
    }
}

impl Collection {
    /// Apply input delta to deck, and its parents.
    /// Caller should ensure transaction.
    pub(crate) fn update_deck_stats(
        &mut self,
        today: u32,
        usn: Usn,
        input: anki_proto::scheduler::UpdateStatsRequest,
    ) -> Result<()> {
        let did = input.deck_id.into();
        let mutator = |c: &mut DeckCommon| {
            c.new_studied += input.new_delta;
            c.review_studied += input.review_delta;
            c.milliseconds_studied += input.millisecond_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            for mut deck in self.storage.parent_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
        }
        Ok(())
    }

    /// Modify the deck's limits by adjusting the 'done today' count.
    /// Positive values increase the limit, negative value decrease it.
    /// If global parent limits are enabled, the deck's parents are adjusted as
    /// well.
    /// Caller should ensure a transaction.
    pub(crate) fn extend_limits(
        &mut self,
        today: u32,
        usn: Usn,
        did: DeckId,
        new_delta: i32,
        review_delta: i32,
    ) -> Result<()> {
        let mutator = |c: &mut DeckCommon| {
            c.new_studied -= new_delta;
            c.review_studied -= review_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            if self.get_config_bool(BoolKey::ApplyAllParentLimits) {
                for mut parent in self.storage.parent_decks(&deck)? {
                    self.update_deck_stats_single(today, usn, &mut parent, mutator)?;
                }
            }
        }

        Ok(())
    }
}

impl Collection {
    fn update_deck_stats_single<F>(
        &mut self,
        today: u32,
        usn: Usn,
        deck: &mut Deck,
        mutator: F,
    ) -> Result<()>
    where
        F: FnOnce(&mut DeckCommon),
    {
        let original = deck.clone();
        deck.reset_stats_if_day_changed(today);
        mutator(&mut deck.common);
        deck.set_modified(usn);
        self.update_single_deck_undoable(deck, original)
    }
}
