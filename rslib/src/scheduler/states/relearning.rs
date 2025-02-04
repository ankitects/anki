// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::interval_kind::IntervalKind;
use super::CardState;
use super::LearnState;
use super::ReviewState;
use super::SchedulingStates;
use super::StateContext;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RelearnState {
    pub learning: LearnState,
    pub review: ReviewState,
}

impl RelearnState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        self.learning.interval_kind()
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        RevlogReviewKind::Relearning
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> SchedulingStates {
        SchedulingStates {
            current: self.into(),
            again: self.answer_again(ctx),
            hard: self.answer_hard(ctx),
            good: self.answer_good(ctx),
            easy: self.answer_easy(ctx).into(),
        }
    }

    fn answer_again(self, ctx: &StateContext) -> CardState {
        let (scheduled_days, memory_state) = self.review.failing_review_interval(ctx);
        if let Some(again_delay) = ctx.relearn_steps.again_delay_secs_learn() {
            RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs: again_delay,
                    elapsed_secs: 0,
                    memory_state,
                },
                review: ReviewState {
                    scheduled_days: scheduled_days.round().max(1.0) as u32,
                    elapsed_days: 0,
                    memory_state,
                    ..self.review
                },
            }
            .into()
        } else if let Some(states) = &ctx.fsrs_next_states {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let interval = states.again.interval;
            let again_review = ReviewState {
                scheduled_days: ctx.with_review_fuzz(interval.round().max(1.0), minimum, maximum),
                memory_state,
                ..self.review
            };
            let again_relearn = RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs: (interval * 86_400.0) as u32,
                    elapsed_secs: 0,
                    memory_state,
                },
                review: again_review,
            };
            if ctx.fsrs_enable_short_term
                && (ctx.fsrs_short_term_with_steps_enabled || ctx.relearn_steps.is_empty())
                && interval < 0.5
            {
                again_relearn.into()
            } else {
                again_review.into()
            }
        } else {
            self.review.into()
        }
    }

    fn answer_hard(self, ctx: &StateContext) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into());
        if let Some(hard_delay) = ctx
            .relearn_steps
            .hard_delay_secs(self.learning.remaining_steps)
        {
            RelearnState {
                learning: LearnState {
                    scheduled_secs: hard_delay,
                    memory_state,
                    ..self.learning
                },
                review: ReviewState {
                    elapsed_days: 0,
                    memory_state,
                    ..self.review
                },
            }
            .into()
        } else if let Some(states) = &ctx.fsrs_next_states {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let interval = states.hard.interval;
            let hard_review = ReviewState {
                scheduled_days: ctx.with_review_fuzz(interval.round().max(1.0), minimum, maximum),
                memory_state,
                ..self.review
            };
            let hard_relearn = RelearnState {
                learning: LearnState {
                    scheduled_secs: (interval * 86_400.0) as u32,
                    memory_state,
                    ..self.learning
                },
                review: hard_review,
            };
            if ctx.fsrs_enable_short_term
                && (ctx.fsrs_short_term_with_steps_enabled || ctx.relearn_steps.is_empty())
                && interval < 0.5
            {
                hard_relearn.into()
            } else {
                hard_review.into()
            }
        } else {
            self.review.into()
        }
    }

    fn answer_good(self, ctx: &StateContext) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.good.memory.into());
        if let Some(good_delay) = ctx
            .relearn_steps
            .good_delay_secs(self.learning.remaining_steps)
        {
            RelearnState {
                learning: LearnState {
                    scheduled_secs: good_delay,
                    remaining_steps: ctx
                        .relearn_steps
                        .remaining_for_good(self.learning.remaining_steps),
                    elapsed_secs: 0,
                    memory_state,
                },
                review: ReviewState {
                    elapsed_days: 0,
                    memory_state,
                    ..self.review
                },
            }
            .into()
        } else if let Some(states) = &ctx.fsrs_next_states {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let interval = states.good.interval;
            let good_review = ReviewState {
                scheduled_days: ctx.with_review_fuzz(interval.round().max(1.0), minimum, maximum),
                memory_state,
                ..self.review
            };
            let good_relearn = RelearnState {
                learning: LearnState {
                    scheduled_secs: (interval * 86_400.0) as u32,
                    remaining_steps: ctx
                        .relearn_steps
                        .remaining_for_good(self.learning.remaining_steps),
                    memory_state,
                    ..self.learning
                },
                review: good_review,
            };
            if ctx.fsrs_enable_short_term
                && (ctx.fsrs_short_term_with_steps_enabled || ctx.relearn_steps.is_empty())
                && interval < 0.5
            {
                good_relearn.into()
            } else {
                good_review.into()
            }
        } else {
            self.review.into()
        }
    }

    fn answer_easy(self, ctx: &StateContext) -> ReviewState {
        let scheduled_days = if let Some(states) = &ctx.fsrs_next_states {
            let (mut minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let good = ctx.with_review_fuzz(states.good.interval, minimum, maximum);
            minimum = good + 1;
            let interval = states.easy.interval;
            ctx.with_review_fuzz(interval.round().max(1.0), minimum, maximum)
        } else {
            self.review.scheduled_days + 1
        };
        ReviewState {
            scheduled_days,
            elapsed_days: 0,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.easy.memory.into()),
            ..self.review
        }
    }
}
