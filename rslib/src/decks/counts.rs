// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{collection::Collection, decks::DeckID, err::Result};
use std::collections::HashMap;

#[derive(Debug)]
pub(crate) struct DueCounts {
    pub new: u32,
    pub review: u32,
    pub learning: u32,
}

impl Collection {
    pub(crate) fn due_counts(
        &mut self,
        limit_to: Option<&str>,
    ) -> Result<HashMap<DeckID, DueCounts>> {
        let days_elapsed = self.timing_today()?.days_elapsed;
        let learn_cutoff = self.learn_cutoff();
        if let Some(limit) = limit_to {
            self.storage
                .due_counts_limited(self.sched_ver(), days_elapsed, learn_cutoff, limit)
        } else {
            self.storage
                .due_counts(self.sched_ver(), days_elapsed, learn_cutoff)
        }
    }
}
