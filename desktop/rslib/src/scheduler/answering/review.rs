// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardStateUpdater;
use super::RevlogEntryPartial;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::scheduler::states::CardState;
use crate::scheduler::states::ReviewState;

impl CardStateUpdater {
    pub(super) fn apply_review_state(
        &mut self,
        current: CardState,
        next: ReviewState,
    ) -> RevlogEntryPartial {
        self.card.queue = CardQueue::Review;
        self.card.ctype = CardType::Review;
        self.card.interval = next.scheduled_days;
        self.card.due = (self.timing.days_elapsed + next.scheduled_days) as i32;
        self.card.ease_factor = (next.ease_factor * 1000.0).round() as u16;
        self.card.lapses = next.lapses;
        self.card.remaining_steps = 0;
        if let Some(position) = current.new_position() {
            self.card.original_position = Some(position)
        }
        self.card.memory_state = next.memory_state;

        RevlogEntryPartial::new(
            current,
            next.into(),
            self.card
                .memory_state
                .map(|d| d.difficulty_shifted())
                .unwrap_or(next.ease_factor),
            self.secs_until_rollover(),
        )
    }
}
