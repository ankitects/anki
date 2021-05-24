// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardStateUpdater, RevlogEntryPartial};
use crate::{
    card::{CardQueue, CardType},
    scheduler::states::{CardState, ReviewState},
};

impl CardStateUpdater {
    pub(super) fn apply_review_state(
        &mut self,
        current: CardState,
        next: ReviewState,
    ) -> Option<RevlogEntryPartial> {
        self.card.queue = CardQueue::Review;
        self.card.ctype = CardType::Review;
        self.card.interval = next.scheduled_days;
        self.card.due = (self.timing.days_elapsed + next.scheduled_days) as i32;
        self.card.ease_factor = (next.ease_factor * 1000.0).round() as u16;
        self.card.lapses = next.lapses;
        self.card.remaining_steps = 0;

        RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            next.ease_factor,
            self.secs_until_rollover(),
        )
    }
}
