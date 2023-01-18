// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::interval_kind::IntervalKind;
use super::normal::NormalState;
use super::CardState;
use super::SchedulingStates;
use super::StateContext;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ReschedulingFilterState {
    pub original_state: NormalState,
}

impl ReschedulingFilterState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        self.original_state.interval_kind()
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        self.original_state.revlog_kind()
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> SchedulingStates {
        let normal = self.original_state.next_states(ctx);
        if ctx.in_filtered_deck {
            SchedulingStates {
                current: self.into(),
                again: maybe_wrap(normal.again),
                hard: maybe_wrap(normal.hard),
                good: maybe_wrap(normal.good),
                easy: maybe_wrap(normal.easy),
            }
        } else {
            // card is marked as filtered, but not in a filtered deck; convert to normal
            normal
        }
    }
}

/// The review state is returned unchanged because cards are returned to
/// their original deck in that state; other normal states are wrapped
/// in the filtered state. Providing a filtered state is an error.
fn maybe_wrap(state: CardState) -> CardState {
    match state {
        CardState::Normal(normal) => {
            if matches!(normal, NormalState::Review(_)) {
                normal.into()
            } else {
                ReschedulingFilterState {
                    original_state: normal,
                }
                .into()
            }
        }
        CardState::Filtered(_) => {
            unreachable!()
        }
    }
}
