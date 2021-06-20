// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use crate::{backend_proto as pb, prelude::*};

#[derive(Debug)]
pub(crate) struct DueCounts {
    pub new: u32,
    pub review: u32,
    pub learning: u32,
}

impl Deck {
    /// Return the studied counts if studied today.
    /// May be negative if user has extended limits.
    pub(crate) fn new_rev_counts(&self, today: u32) -> (i32, i32) {
        if self.common.last_day_studied == today {
            (self.common.new_studied, self.common.review_studied)
        } else {
            (0, 0)
        }
    }
}

impl Collection {
    /// Get due counts for decks at the given timestamp.
    pub(crate) fn due_counts(
        &mut self,
        days_elapsed: u32,
        learn_cutoff: u32,
        limit_to: Option<&str>,
        v3: bool,
    ) -> Result<HashMap<DeckId, DueCounts>> {
        self.storage.due_counts(
            self.scheduler_version(),
            days_elapsed,
            learn_cutoff,
            limit_to,
            v3,
        )
    }

    pub(crate) fn counts_for_deck_today(
        &mut self,
        did: DeckId,
    ) -> Result<pb::CountsForDeckTodayResponse> {
        let today = self.current_due_day(0)?;
        let mut deck = self.storage.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        deck.reset_stats_if_day_changed(today);
        Ok(pb::CountsForDeckTodayResponse {
            new: deck.common.new_studied,
            review: deck.common.review_studied,
        })
    }
}
