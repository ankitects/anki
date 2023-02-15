// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rand::prelude::*;
use rand::rngs::StdRng;

use super::CardStateUpdater;
use super::RevlogEntryPartial;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::prelude::*;
use crate::scheduler::states::CardState;
use crate::scheduler::states::IntervalKind;
use crate::scheduler::states::LearnState;
use crate::scheduler::states::NewState;

impl CardStateUpdater {
    pub(super) fn apply_new_state(
        &mut self,
        current: CardState,
        next: NewState,
    ) -> RevlogEntryPartial {
        self.card.ctype = CardType::New;
        self.card.queue = CardQueue::New;
        self.card.due = next.position as i32;
        self.card.original_position = None;

        RevlogEntryPartial::new(current, next.into(), 0.0, self.secs_until_rollover())
    }

    pub(super) fn apply_learning_state(
        &mut self,
        current: CardState,
        next: LearnState,
    ) -> RevlogEntryPartial {
        self.card.remaining_steps = next.remaining_steps;
        self.card.ctype = CardType::Learn;
        if let Some(position) = current.new_position() {
            self.card.original_position = Some(position)
        }

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

        RevlogEntryPartial::new(current, next.into(), 0.0, self.secs_until_rollover())
    }

    /// Adds secs + fuzz to current time
    pub(super) fn fuzzed_next_learning_timestamp(&self, secs: u32) -> i32 {
        TimestampSecs::now().0 as i32 + self.with_learning_fuzz(secs) as i32
    }

    /// Add up to 25% increase to seconds, but no more than 5 minutes.
    fn with_learning_fuzz(&self, secs: u32) -> u32 {
        if let Some(seed) = self.fuzz_seed {
            let mut rng = StdRng::seed_from_u64(seed);
            let upper_exclusive = secs + ((secs as f32) * 0.25).min(300.0).floor() as u32;
            if secs >= upper_exclusive {
                secs
            } else {
                rng.gen_range(secs..upper_exclusive)
            }
        } else {
            secs
        }
    }
}
