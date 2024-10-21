// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Retrievability;
use fsrs::FSRS;

use crate::card::CardQueue;
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
            now: Default::default(),
            next_day_at: Default::default(),
        };
        let fsrs = FSRS::new(None).unwrap();
        // note id -> (sum, count)
        let mut note_retrievability: std::collections::HashMap<i64, (f32, u32)> =
            std::collections::HashMap::new();
        for card in &self.cards {
            if card.queue == CardQueue::Suspended {
                continue;
            }
            let entry = note_retrievability
                .entry(card.note_id.0)
                .or_insert((0.0, 0));
            entry.1 += 1;
            if let Some(state) = card.memory_state {
                let elapsed_days = card.days_since_last_review(&timing).unwrap_or_default();
                let r = fsrs.current_retrievability(state.into(), elapsed_days);

                *retrievability
                    .retrievability
                    .entry(percent_to_bin(r * 100.0))
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
