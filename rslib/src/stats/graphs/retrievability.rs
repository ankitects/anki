// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Retrievability;
use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;

use crate::prelude::TimestampSecs;
use crate::scheduler::timing::SchedTimingToday;
use crate::stats::graphs::eases::percent_to_bin;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    /// (SM-2, FSRS)
    pub(super) fn retrievability(&self) -> Retrievability {
        let mut retrievability = Retrievability::default();
        let mut card_with_retrievability_count: usize = 0;
        let timing = SchedTimingToday {
            days_elapsed: self.days_elapsed,
            now: TimestampSecs::now(),
            next_day_at: self.next_day_start,
        };
        let fsrs = FSRS::new(None).unwrap();
        // note id -> (sum, count)
        let mut note_retrievability: std::collections::HashMap<i64, (f32, u32)> =
            std::collections::HashMap::new();
        for card in &self.cards {
            let entry = note_retrievability
                .entry(card.note_id.0)
                .or_insert((0.0, 0));
            entry.1 += 1;
            if let Some(state) = card.memory_state {
                let elapsed_seconds = card.seconds_since_last_review(&timing).unwrap_or_default();
                let r = fsrs.current_retrievability_seconds(
                    state.into(),
                    elapsed_seconds,
                    card.decay.unwrap_or(FSRS5_DEFAULT_DECAY),
                );

                *retrievability
                    .retrievability
                    .entry(percent_to_bin(r * 100.0, 1))
                    .or_insert_with(Default::default) += 1;
                retrievability.sum_by_card += r;
                card_with_retrievability_count += 1;
                entry.0 += r;
            } else {
                entry.0 += 0.0;
            }
        }
        if card_with_retrievability_count != 0 {
            retrievability.average =
                retrievability.sum_by_card * 100.0 / card_with_retrievability_count as f32;
        }
        retrievability.sum_by_note = note_retrievability
            .values()
            .map(|(sum, count)| sum / *count as f32)
            .sum();
        retrievability
    }
}
