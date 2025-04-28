// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::get_fuzz_seed_for_id_and_reps;
use super::CardStateUpdater;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::decks::DeckKind;
use crate::scheduler::states::CardState;
use crate::scheduler::states::LearnState;
use crate::scheduler::states::NewState;
use crate::scheduler::states::NormalState;
use crate::scheduler::states::PreviewState;
use crate::scheduler::states::RelearnState;
use crate::scheduler::states::ReschedulingFilterState;
use crate::scheduler::states::ReviewState;

impl CardStateUpdater {
    pub(crate) fn current_card_state(&self) -> CardState {
        let due = match &self.deck.kind {
            DeckKind::Normal(_) => {
                // if not in a filtered deck, ensure due time is not before today,
                // which avoids tripping up test_nextIvl() in the Python tests
                if matches!(self.card.ctype, CardType::Review) {
                    self.card.due.min(self.timing.days_elapsed as i32)
                } else {
                    self.card.due
                }
            }
            DeckKind::Filtered(_) => {
                if self.card.original_due != 0 {
                    self.card.original_due
                } else {
                    self.card.due
                }
            }
        };

        let normal_state = self.normal_study_state(due);

        match &self.deck.kind {
            // normal decks have normal state
            DeckKind::Normal(_) => normal_state.into(),
            // filtered decks wrap the normal state
            DeckKind::Filtered(filtered) => {
                if filtered.reschedule {
                    ReschedulingFilterState {
                        original_state: normal_state,
                    }
                    .into()
                } else {
                    PreviewState {
                        scheduled_secs: filtered.preview_again_secs,
                        finished: false,
                    }
                    .into()
                }
            }
        }
    }

    fn normal_study_state(&self, due: i32) -> NormalState {
        let interval = self.card.interval;
        let lapses = self.card.lapses;
        let ease_factor = self.card.ease_factor();
        let remaining_steps = self.card.remaining_steps();
        let memory_state = self.card.memory_state;
        let elapsed_secs = |last_ivl: u32| {
            match self.card.queue {
                CardQueue::Learn => {
                    // Decrease reps by 1 to get correct seed for fuzz.
                    // If the fuzz calculation changes, this will break.
                    let last_ivl_with_fuzz = self.learning_ivl_with_fuzz(
                        get_fuzz_seed_for_id_and_reps(
                            self.card.id,
                            if self.card.reps > 0 { self.card.reps - 1 } else { 0 }
                        ),
                        last_ivl,
                    );
                    let last_answered_time = due as i64 - last_ivl_with_fuzz as i64;
                    (self.now.0 - last_answered_time) as u32
                }
                CardQueue::DayLearn => {
                    let days_since_col_creation = self.timing.days_elapsed as i32;
                    // Need .max(1) for same day learning cards pushed to the next day.
                    // 86_400 is the number of seconds in a day.
                    let last_ivl_as_days = (last_ivl / 86_400).max(1) as i32;
                    let elapsed_days = days_since_col_creation - due + last_ivl_as_days;
                    (elapsed_days * 86_400) as u32
                }
                _ => 0, // Not used for other card queues.
            }
        };

        match self.card.ctype {
            CardType::New => NormalState::New(NewState {
                position: due.max(0) as u32,
            }),
            CardType::Learn => {
                let last_ivl = self.learn_steps().current_delay_secs(remaining_steps);
                LearnState {
                    scheduled_secs: last_ivl,
                    remaining_steps,
                    elapsed_secs: elapsed_secs(last_ivl),
                    memory_state,
                }
            }
            .into(),
            CardType::Review => ReviewState {
                scheduled_days: interval,
                elapsed_days: ((interval as i32) - (due - self.timing.days_elapsed as i32)).max(0)
                    as u32,
                ease_factor,
                lapses,
                leeched: false,
                memory_state,
            }
            .into(),
            CardType::Relearn => {
                let last_ivl = self.relearn_steps().current_delay_secs(remaining_steps);
                RelearnState {
                    learning: LearnState {
                        scheduled_secs: last_ivl,
                        elapsed_secs: elapsed_secs(last_ivl),
                        remaining_steps,
                        memory_state,
                    },
                    review: ReviewState {
                        scheduled_days: interval,
                        elapsed_days: interval,
                        ease_factor,
                        lapses,
                        leeched: false,
                        memory_state,
                    },
                }
            }
            .into(),
        }
    }
}
