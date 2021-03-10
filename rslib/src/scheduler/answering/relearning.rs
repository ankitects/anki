// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{CardQueue, CardType},
    prelude::*,
    scheduler::states::{CardState, IntervalKind, RelearnState},
};

use super::{CardStateUpdater, RevlogEntryPartial};

impl CardStateUpdater {
    pub(super) fn apply_relearning_state(
        &mut self,
        current: CardState,
        next: RelearnState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.card.interval = next.review.scheduled_days;
        self.card.remaining_steps = next.learning.remaining_steps;
        self.card.ctype = CardType::Relearn;
        self.card.lapses = next.review.lapses;

        let interval = next
            .interval_kind()
            .maybe_as_days(self.secs_until_rollover());
        match interval {
            IntervalKind::InSecs(secs) => {
                self.card.queue = CardQueue::Learn;
                self.card.due = TimestampSecs::now().0 as i32 + secs as i32;
            }
            IntervalKind::InDays(days) => {
                self.card.queue = CardQueue::DayLearn;
                self.card.due = (self.timing.days_elapsed + days) as i32;
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            next.review.ease_factor,
            self.secs_until_rollover(),
        ))
    }
}
