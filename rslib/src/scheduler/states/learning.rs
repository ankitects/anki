// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::interval_kind::IntervalKind;
use super::CardState;
use super::ReviewState;
use super::SchedulingStates;
use super::StateContext;
use crate::card::FsrsMemoryState;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LearnState {
    pub remaining_steps: u32,
    pub scheduled_secs: u32,
    pub elapsed_secs: u32,
    pub memory_state: Option<FsrsMemoryState>,
}

impl LearnState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        IntervalKind::InSecs(self.scheduled_secs)
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        RevlogReviewKind::Learning
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> SchedulingStates {
        SchedulingStates {
            current: self.into(),
            again: self.answer_again(ctx).into(),
            hard: self.answer_hard(ctx).into(),
            good: self.answer_good(ctx),
            easy: self.answer_easy(ctx).into(),
        }
    }

    fn answer_again(self, ctx: &StateContext) -> LearnState {
        LearnState {
            remaining_steps: ctx.steps.remaining_for_failed(),
            scheduled_secs: ctx.steps.again_delay_secs_learn(),
            elapsed_secs: 0,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.again.memory.into()),
        }
    }

    fn answer_hard(self, ctx: &StateContext) -> LearnState {
        LearnState {
            scheduled_secs: ctx
                .steps
                .hard_delay_secs(self.remaining_steps)
                // user has 0 learning steps, which the UI doesn't allow
                .unwrap_or(60),
            elapsed_secs: 0,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into()),
            ..self
        }
    }

    fn answer_good(self, ctx: &StateContext) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.good.memory.into());
        if let Some(good_delay) = ctx.steps.good_delay_secs(self.remaining_steps) {
            LearnState {
                remaining_steps: ctx.steps.remaining_for_good(self.remaining_steps),
                scheduled_secs: good_delay,
                elapsed_secs: 0,
                memory_state,
            }
            .into()
        } else {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let interval = if let Some(states) = &ctx.fsrs_next_states {
                states.good.interval
            } else {
                ctx.graduating_interval_good
            };
            ReviewState {
                scheduled_days: ctx.with_review_fuzz(interval as f32, minimum, maximum),
                ease_factor: ctx.initial_ease_factor,
                memory_state,
                ..Default::default()
            }
            .into()
        }
    }

    fn answer_easy(self, ctx: &StateContext) -> ReviewState {
        let (mut minimum, maximum) = ctx.min_and_max_review_intervals(1);
        let interval = if let Some(states) = &ctx.fsrs_next_states {
            let good = ctx.with_review_fuzz(states.good.interval as f32, minimum, maximum);
            minimum = good + 1;
            states.easy.interval
        } else {
            ctx.graduating_interval_easy
        };
        ReviewState {
            scheduled_days: ctx.with_review_fuzz(interval as f32, minimum, maximum),
            ease_factor: ctx.initial_ease_factor,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.easy.memory.into()),
            ..Default::default()
        }
    }
}
