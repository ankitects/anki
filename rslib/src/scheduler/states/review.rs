// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::interval_kind::IntervalKind;
use super::CardState;
use super::LearnState;
use super::RelearnState;
use super::SchedulingStates;
use super::StateContext;
use crate::revlog::RevlogReviewKind;

pub const INITIAL_EASE_FACTOR: f32 = 2.5;
pub const MINIMUM_EASE_FACTOR: f32 = 1.3;
pub const EASE_FACTOR_AGAIN_DELTA: f32 = -0.2;
pub const EASE_FACTOR_HARD_DELTA: f32 = -0.15;
pub const EASE_FACTOR_EASY_DELTA: f32 = 0.15;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ReviewState {
    pub scheduled_days: u32,
    pub elapsed_days: u32,
    pub ease_factor: f32,
    pub lapses: u32,
    pub leeched: bool,
}

impl Default for ReviewState {
    fn default() -> Self {
        ReviewState {
            scheduled_days: 0,
            elapsed_days: 0,
            ease_factor: INITIAL_EASE_FACTOR,
            lapses: 0,
            leeched: false,
        }
    }
}

impl ReviewState {
    pub(crate) fn days_late(&self) -> i32 {
        self.elapsed_days as i32 - self.scheduled_days as i32
    }

    pub(crate) fn interval_kind(self) -> IntervalKind {
        // fixme: maybe use elapsed days in the future? would only
        // make sense for revlog's lastIvl, not for future interval
        IntervalKind::InDays(self.scheduled_days)
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        if self.days_late() < 0 {
            RevlogReviewKind::Filtered
        } else {
            RevlogReviewKind::Review
        }
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> SchedulingStates {
        let (hard_interval, good_interval, easy_interval) = self.passing_review_intervals(ctx);

        SchedulingStates {
            current: self.into(),
            again: self.answer_again(ctx),
            hard: self.answer_hard(hard_interval).into(),
            good: self.answer_good(good_interval).into(),
            easy: self.answer_easy(easy_interval).into(),
        }
    }

    pub(crate) fn failing_review_interval(self, ctx: &StateContext) -> u32 {
        (((self.scheduled_days as f32) * ctx.lapse_multiplier) as u32)
            .max(ctx.minimum_lapse_interval)
            .max(1)
    }

    fn answer_again(self, ctx: &StateContext) -> CardState {
        let lapses = self.lapses + 1;
        let leeched = leech_threshold_met(lapses, ctx.leech_threshold);
        let again_review = ReviewState {
            scheduled_days: self.failing_review_interval(ctx),
            elapsed_days: 0,
            ease_factor: (self.ease_factor + EASE_FACTOR_AGAIN_DELTA).max(MINIMUM_EASE_FACTOR),
            lapses,
            leeched,
        };

        if let Some(again_delay) = ctx.relearn_steps.again_delay_secs_relearn() {
            RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs: again_delay,
                },
                review: again_review,
            }
            .into()
        } else {
            again_review.into()
        }
    }

    fn answer_hard(self, scheduled_days: u32) -> ReviewState {
        ReviewState {
            scheduled_days,
            elapsed_days: 0,
            ease_factor: (self.ease_factor + EASE_FACTOR_HARD_DELTA).max(MINIMUM_EASE_FACTOR),
            ..self
        }
    }

    fn answer_good(self, scheduled_days: u32) -> ReviewState {
        ReviewState {
            scheduled_days,
            elapsed_days: 0,
            ..self
        }
    }

    fn answer_easy(self, scheduled_days: u32) -> ReviewState {
        ReviewState {
            scheduled_days,
            elapsed_days: 0,
            ease_factor: self.ease_factor + EASE_FACTOR_EASY_DELTA,
            ..self
        }
    }

    /// Return the intervals for hard, good and easy, each of which depends on
    /// the previous.
    fn passing_review_intervals(self, ctx: &StateContext) -> (u32, u32, u32) {
        if self.days_late() < 0 {
            self.passing_early_review_intervals(ctx)
        } else {
            self.passing_nonearly_review_intervals(ctx)
        }
    }

    fn passing_nonearly_review_intervals(self, ctx: &StateContext) -> (u32, u32, u32) {
        let current_interval = self.scheduled_days as f32;
        let days_late = self.days_late().max(0) as f32;

        // hard
        let hard_factor = ctx.hard_multiplier;
        let hard_minimum = if hard_factor <= 1.0 {
            0
        } else {
            self.scheduled_days + 1
        };
        let hard_interval =
            constrain_passing_interval(ctx, current_interval * hard_factor, hard_minimum, true);
        // good
        let good_minimum = if hard_factor <= 1.0 {
            self.scheduled_days + 1
        } else {
            hard_interval + 1
        };
        let good_interval = constrain_passing_interval(
            ctx,
            (current_interval + days_late / 2.0) * self.ease_factor,
            good_minimum,
            true,
        );
        // easy
        let easy_interval = constrain_passing_interval(
            ctx,
            (current_interval + days_late) * self.ease_factor * ctx.easy_multiplier,
            good_interval + 1,
            true,
        );

        (hard_interval, good_interval, easy_interval)
    }

    /// Mostly direct port from the Python version for now, so we can confirm
    /// implementation is correct.
    /// FIXME: this needs reworking in the future; it overly penalizes reviews
    /// done shortly before the due date.
    fn passing_early_review_intervals(self, ctx: &StateContext) -> (u32, u32, u32) {
        let scheduled = self.scheduled_days as f32;
        let elapsed = (self.scheduled_days as f32) + (self.days_late() as f32);

        let hard_interval = {
            let factor = ctx.hard_multiplier;
            let half_usual = factor / 2.0;
            constrain_passing_interval(
                ctx,
                (elapsed * factor).max(scheduled * half_usual),
                0,
                false,
            )
        };

        let good_interval =
            constrain_passing_interval(ctx, (elapsed * self.ease_factor).max(scheduled), 0, false);

        let easy_interval = {
            let reduced_bonus = ctx.easy_multiplier - (ctx.easy_multiplier - 1.0) / 2.0;
            constrain_passing_interval(
                ctx,
                (elapsed * self.ease_factor).max(scheduled) * reduced_bonus,
                0,
                false,
            )
        };

        (hard_interval, good_interval, easy_interval)
    }
}

/// True when lapses is at threshold, or every half threshold after that.
/// Non-even thresholds round up the half threshold.
fn leech_threshold_met(lapses: u32, threshold: u32) -> bool {
    if threshold > 0 {
        let half_threshold = (threshold as f32 / 2.0).ceil().max(1.0) as u32;
        // at threshold, and every half threshold after that, rounding up
        lapses >= threshold && (lapses - threshold) % half_threshold == 0
    } else {
        false
    }
}

/// Transform the provided hard/good/easy interval.
/// - Apply configured interval multiplier.
/// - Apply fuzz.
/// - Ensure it is at least `minimum`, and at least 1.
/// - Ensure it is at or below the configured maximum interval.
fn constrain_passing_interval(ctx: &StateContext, interval: f32, minimum: u32, fuzz: bool) -> u32 {
    let interval = interval * ctx.interval_multiplier;
    let (minimum, maximum) = ctx.min_and_max_review_intervals(minimum);
    if fuzz {
        ctx.with_review_fuzz(interval, minimum, maximum)
    } else {
        (interval.round() as u32).clamp(minimum, maximum)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn leech_threshold() {
        assert!(!leech_threshold_met(0, 3));
        assert!(!leech_threshold_met(1, 3));
        assert!(!leech_threshold_met(2, 3));
        assert!(leech_threshold_met(3, 3));
        assert!(!leech_threshold_met(4, 3));
        assert!(leech_threshold_met(5, 3));
        assert!(!leech_threshold_met(6, 3));
        assert!(leech_threshold_met(7, 3));

        assert!(!leech_threshold_met(7, 8));
        assert!(leech_threshold_met(8, 8));
        assert!(!leech_threshold_met(9, 8));
        assert!(!leech_threshold_met(10, 8));
        assert!(!leech_threshold_met(11, 8));
        assert!(leech_threshold_met(12, 8));
        assert!(!leech_threshold_met(13, 8));

        // 0 means off
        assert!(!leech_threshold_met(0, 0));

        // no div by zero; half of 1 is 1
        assert!(!leech_threshold_met(0, 1));
        assert!(leech_threshold_met(1, 1));
        assert!(leech_threshold_met(2, 1));
        assert!(leech_threshold_met(3, 1));
    }

    #[test]
    fn extreme_multiplier_fuzz() {
        let mut ctx = StateContext::defaults_for_testing();
        // our calculations should work correctly with a low ease or non-default
        // multiplier
        let state = ReviewState {
            scheduled_days: 1,
            elapsed_days: 1,
            ease_factor: 1.3,
            lapses: 0,
            leeched: false,
        };
        ctx.fuzz_factor = Some(0.0);
        assert_eq!(state.passing_review_intervals(&ctx), (2, 3, 4));

        // this is a silly multiplier, but it shouldn't underflow
        ctx.interval_multiplier = 0.1;
        assert_eq!(state.passing_review_intervals(&ctx), (2, 3, 4));
        ctx.fuzz_factor = Some(0.99);
        assert_eq!(state.passing_review_intervals(&ctx), (2, 4, 6));

        // maximum must be respected no matter what
        ctx.interval_multiplier = 10.0;
        ctx.maximum_review_interval = 5;
        assert_eq!(state.passing_review_intervals(&ctx), (5, 5, 5));
    }

    #[test]
    fn low_hard_multiplier_does_not_pull_good_down() {
        let mut ctx = StateContext::defaults_for_testing();
        // our calculations should work correctly with a low ease or non-default
        // multiplier
        ctx.hard_multiplier = 0.1;
        let state = ReviewState {
            scheduled_days: 2,
            elapsed_days: 2,
            ease_factor: 1.3,
            lapses: 0,
            leeched: false,
        };
        ctx.fuzz_factor = Some(0.0);
        assert_eq!(state.passing_review_intervals(&ctx), (1, 3, 4));
    }
}
