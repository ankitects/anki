// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardStateUpdater;
use crate::{
    card::CardType,
    decks::DeckKind,
    scheduler::states::{
        CardState, LearnState, NewState, NormalState, PreviewState, RelearnState,
        ReschedulingFilterState, ReviewState,
    },
};

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
                    // v2 scheduler resets original_due on first answer
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
                        scheduled_secs: filtered.preview_delay * 60,
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

        match self.card.ctype {
            CardType::New => NormalState::New(NewState {
                position: due.max(0) as u32,
            }),
            CardType::Learn => {
                LearnState {
                    scheduled_secs: self.learn_steps().current_delay_secs(remaining_steps),
                    remaining_steps,
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
            }
            .into(),
            CardType::Relearn => RelearnState {
                learning: LearnState {
                    scheduled_secs: self.relearn_steps().current_delay_secs(remaining_steps),
                    remaining_steps,
                },
                review: ReviewState {
                    scheduled_days: interval,
                    elapsed_days: interval,
                    ease_factor,
                    lapses,
                    leeched: false,
                },
            }
            .into(),
        }
    }
}
