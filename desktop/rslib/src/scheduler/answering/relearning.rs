// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardStateUpdater;
use super::RevlogEntryPartial;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::scheduler::states::CardState;
use crate::scheduler::states::IntervalKind;
use crate::scheduler::states::RelearnState;

impl CardStateUpdater {
    pub(super) fn apply_relearning_state(
        &mut self,
        current: CardState,
        next: RelearnState,
    ) -> RevlogEntryPartial {
        self.card.interval = next.review.scheduled_days;
        self.card.remaining_steps = next.learning.remaining_steps;
        self.card.ctype = CardType::Relearn;
        self.card.lapses = next.review.lapses;
        self.card.ease_factor = (next.review.ease_factor * 1000.0).round() as u16;
        if let Some(position) = current.new_position() {
            self.card.original_position = Some(position)
        }
        self.card.memory_state = next.learning.memory_state;

        let interval = next
            .interval_kind()
            .maybe_as_days(self.secs_until_rollover());
        match interval {
            IntervalKind::InSecs(secs) => {
                self.card.queue = CardQueue::Learn;
                self.card.due = self.fuzzed_next_learning_timestamp(secs);
            }
            IntervalKind::InDays(days) => {
                self.card.queue = CardQueue::DayLearn;
                self.card.due = (self.timing.days_elapsed + days) as i32;
            }
        }

        RevlogEntryPartial::new(
            current,
            next.into(),
            self.card
                .memory_state
                .map(|d| d.difficulty_shifted())
                .unwrap_or(next.review.ease_factor),
            self.secs_until_rollover(),
        )
    }
}
