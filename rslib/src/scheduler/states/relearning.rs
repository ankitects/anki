// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{
    interval_kind::IntervalKind, CardState, LearnState, NextCardStates, ReviewState, StateContext,
};
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

    pub(crate) fn next_states(self, ctx: &StateContext) -> NextCardStates {
        NextCardStates {
            current: self.into(),
            again: self.answer_again(ctx),
            hard: self.answer_hard(ctx),
            good: self.answer_good(ctx),
            easy: self.answer_easy().into(),
        }
    }

    fn answer_again(self, ctx: &StateContext) -> CardState {
        if let Some(again_delay) = ctx.relearn_steps.again_delay_secs_relearn() {
            RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs: again_delay,
                },
                review: ReviewState {
                    scheduled_days: self.review.failing_review_interval(ctx),
                    elapsed_days: 0,
                    ..self.review
                },
            }
            .into()
        } else {
            self.review.into()
        }
    }

    fn answer_hard(self, ctx: &StateContext) -> CardState {
        if let Some(hard_delay) = ctx
            .relearn_steps
            .hard_delay_secs(self.learning.remaining_steps)
        {
            RelearnState {
                learning: LearnState {
                    scheduled_secs: hard_delay,
                    ..self.learning
                },
                review: ReviewState {
                    elapsed_days: 0,
                    ..self.review
                },
            }
            .into()
        } else {
            self.review.into()
        }
    }

    fn answer_good(self, ctx: &StateContext) -> CardState {
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
                },
                review: ReviewState {
                    elapsed_days: 0,
                    ..self.review
                },
            }
            .into()
        } else {
            self.review.into()
        }
    }

    fn answer_easy(self) -> ReviewState {
        ReviewState {
            scheduled_days: self.review.scheduled_days + 1,
            elapsed_days: 0,
            ..self.review
        }
    }
}
