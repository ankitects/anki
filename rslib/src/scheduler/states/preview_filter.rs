// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
            again: PreviewState {
                scheduled_secs: ctx.preview_step * 60,
                ..self
            }
            .into(),
            hard: PreviewState {
                // ~15 minutes with the default setting
                scheduled_secs: ctx.preview_step * 90,
                ..self
            }
            .into(),
            good: PreviewState {
                scheduled_secs: ctx.preview_step * 120,
                ..self
            }
            .into(),
            easy: PreviewState {
                scheduled_secs: 0,
                finished: true,
            }
            .into(),
        }
    }
}
