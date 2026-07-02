// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardState;
use super::IntervalKind;
use super::SchedulingStates;
use super::StateContext;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PreviewState {
    pub scheduled_secs: u32,
    pub finished: bool,
}

impl PreviewState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        IntervalKind::InSecs(self.scheduled_secs)
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        RevlogReviewKind::Filtered
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> SchedulingStates {
        SchedulingStates {
            current: self.into(),
            again: delay_or_return(ctx.preview_delays.again),
            hard: delay_or_return(ctx.preview_delays.hard),
            good: delay_or_return(ctx.preview_delays.good),
            easy: delay_or_return(0),
        }
    }
}

fn delay_or_return(seconds: u32) -> CardState {
    if seconds == 0 {
        PreviewState {
            scheduled_secs: 0,
            finished: true,
        }
    } else {
        PreviewState {
            scheduled_secs: seconds,
            finished: false,
        }
    }
    .into()
}
