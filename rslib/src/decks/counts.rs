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
    /// Get due counts for decks at the given timestamp.
    pub(crate) fn due_counts(
        &mut self,
        days_elapsed: u32,
        learn_cutoff: u32,
        limit_to: Option<&str>,
    ) -> Result<HashMap<DeckID, DueCounts>> {
        self.storage
            .due_counts(self.sched_ver(), days_elapsed, learn_cutoff, limit_to)
    }
}
