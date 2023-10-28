// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Retrievability;
use fsrs::FSRS;

use crate::scheduler::timing::SchedTimingToday;
use crate::stats::graphs::eases::percent_to_bin;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    /// (SM-2, FSRS)
    pub(super) fn retrievability(&self) -> Retrievability {
        let mut retrievability = Retrievability::default();
        let timing = SchedTimingToday {
            days_elapsed: self.days_elapsed,
            now: Default::default(),
            next_day_at: Default::default(),
        };
        let fsrs = FSRS::new(None).unwrap();
        for card in &self.cards {
            if let Some(state) = card.memory_state {
                let r = fsrs.current_retrievability(
                    state.into(),
                    card.days_since_last_review(&timing).unwrap_or_default(),
                );
                *retrievability
                    .retrievability
                    .entry(percent_to_bin(r * 100.0))
                    .or_insert_with(Default::default) += 1;
            }
        }
        retrievability
    }
}
