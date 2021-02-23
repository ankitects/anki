// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{IntervalKind, NextCardStates, NormalState, StateContext};

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct PreviewState {
    pub scheduled_secs: u32,
    pub original_state: NormalState,
}

impl PreviewState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        IntervalKind::InSecs(self.scheduled_secs)
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> NextCardStates {
        NextCardStates {
            current: self.into(),
            again: PreviewState {
                scheduled_secs: ctx.with_learning_fuzz(ctx.preview_step * 60),
                ..self
            }
            .into(),
            hard: self.original_state.into(),
            good: self.original_state.into(),
            easy: self.original_state.into(),
        }
    }
}
