// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{CardQueue, CardType},
    prelude::*,
    scheduler::states::{CardState, IntervalKind, LearnState, NewState},
};

use super::{CardStateUpdater, RevlogEntryPartial};

impl CardStateUpdater {
    pub(super) fn apply_new_state(
        &mut self,
        current: CardState,
        next: NewState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.card.ctype = CardType::New;
        self.card.queue = CardQueue::New;
        self.card.due = next.position as i32;

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            self.secs_until_rollover(),
        ))
    }

    pub(super) fn apply_learning_state(
        &mut self,
        current: CardState,
        next: LearnState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.card.remaining_steps = next.remaining_steps;
        self.card.ctype = CardType::Learn;

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
            0.0,
            self.secs_until_rollover(),
        ))
    }
}
