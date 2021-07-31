// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{interval_kind::IntervalKind, CardState, NextCardStates, ReviewState, StateContext};
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LearnState {
    pub remaining_steps: u32,
    pub scheduled_secs: u32,
}

impl LearnState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        IntervalKind::InSecs(self.scheduled_secs)
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        RevlogReviewKind::Learning
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> NextCardStates {
        NextCardStates {
            current: self.into(),
            again: self.answer_again(ctx).into(),
            hard: self.answer_hard(ctx),
            good: self.answer_good(ctx),
            easy: self.answer_easy(ctx).into(),
        }
    }

    fn answer_again(self, ctx: &StateContext) -> LearnState {
        LearnState {
            remaining_steps: ctx.steps.remaining_for_failed(),
            scheduled_secs: ctx.steps.again_delay_secs_learn(),
        }
    }

    fn answer_hard(self, ctx: &StateContext) -> CardState {
        if let Some(hard_delay) = ctx.steps.hard_delay_secs(self.remaining_steps) {
            LearnState {
                scheduled_secs: hard_delay,
                ..self
            }
            .into()
        } else {
            // steps modified while card in learning
            ReviewState {
                scheduled_days: ctx.fuzzed_graduating_interval_good(),
                ease_factor: ctx.initial_ease_factor,
                ..Default::default()
            }
            .into()
        }
    }

    fn answer_good(self, ctx: &StateContext) -> CardState {
        if let Some(good_delay) = ctx.steps.good_delay_secs(self.remaining_steps) {
            LearnState {
                remaining_steps: ctx.steps.remaining_for_good(self.remaining_steps),
                scheduled_secs: good_delay,
            }
            .into()
        } else {
            ReviewState {
                scheduled_days: ctx.fuzzed_graduating_interval_good(),
                ease_factor: ctx.initial_ease_factor,
                ..Default::default()
            }
            .into()
        }
    }

    fn answer_easy(self, ctx: &StateContext) -> ReviewState {
        ReviewState {
            scheduled_days: ctx.fuzzed_graduating_interval_easy(),
            ease_factor: ctx.initial_ease_factor,
            ..Default::default()
        }
    }
}
