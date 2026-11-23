// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::button_intervals::button_intervals;
use super::button_intervals::ButtonInterval;
use super::button_intervals::DayRule;
use super::interval_kind::IntervalKind;
use super::CardState;
use super::LearnState;
use super::RelearnState;
use super::SchedulingStates;
use super::StateContext;
use crate::card::FsrsMemoryState;
use crate::revlog::RevlogReviewKind;

pub const INITIAL_EASE_FACTOR: f32 = 2.5;
pub const MINIMUM_EASE_FACTOR: f32 = 1.3;
pub const EASE_FACTOR_AGAIN_DELTA: f32 = -0.2;
pub const EASE_FACTOR_HARD_DELTA: f32 = -0.15;
pub const EASE_FACTOR_EASY_DELTA: f32 = 0.15;
const YOUNG_LEECH_THRESHOLD_DAYS: u32 = 21;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ReviewState {
    pub scheduled_days: u32,
    pub fuzz_delta_days: i32,
    pub elapsed_days: u32,
    pub ease_factor: f32,
    pub lapses: u32,
    pub leeched: bool,
    pub memory_state: Option<FsrsMemoryState>,
}

impl Default for ReviewState {
    fn default() -> Self {
        ReviewState {
            scheduled_days: 0,
            fuzz_delta_days: 0,
            elapsed_days: 0,
            ease_factor: INITIAL_EASE_FACTOR,
            lapses: 0,
            leeched: false,
            memory_state: None,
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
        if let Some(states) = &ctx.fsrs_next_states {
            let again_step = ctx.fsrs_uses_learning_queues()
                && ctx.relearn_steps.again_delay_secs_learn().is_some();
            let intervals = button_intervals(
                ctx,
                [
                    (!again_step).then_some(states.again.interval),
                    Some(states.hard.interval),
                    Some(states.good.interval),
                    Some(states.easy.interval),
                ],
                DayRule::Review {
                    previous_interval: self.scheduled_days,
                },
            );
            let passing = |interval: Option<ButtonInterval>,
                           answer: fn(Self, u32, i32, &StateContext) -> ReviewState|
             -> CardState {
                match interval {
                    Some(ButtonInterval::Days {
                        days,
                        fuzz_delta_days,
                    }) => answer(self, days, fuzz_delta_days, ctx).into(),
                    Some(ButtonInterval::Secs(scheduled_secs)) => {
                        let review = answer(self, 1, 0, ctx);
                        RelearnState {
                            learning: LearnState {
                                remaining_steps: 0,
                                scheduled_secs,
                                elapsed_secs: 0,
                                memory_state: review.memory_state,
                            },
                            review,
                        }
                        .into()
                    }
                    None => unreachable!("passing buttons always have an interval"),
                }
            };
            return SchedulingStates {
                current: self.into(),
                again: self.answer_again(ctx, intervals[0]),
                hard: passing(intervals[1], Self::answer_hard),
                good: passing(intervals[2], Self::answer_good),
                easy: passing(intervals[3], Self::answer_easy),
                dynamic_desired_retentions: None,
                dynamic_desired_retention_enabled: false,
            };
        }

        let (hard_interval, good_interval, easy_interval) = self.passing_review_intervals(ctx);

        SchedulingStates {
            current: self.into(),
            again: self.answer_again(ctx, None),
            hard: self
                .answer_hard(hard_interval.0, hard_interval.1, ctx)
                .into(),
            good: self
                .answer_good(good_interval.0, good_interval.1, ctx)
                .into(),
            easy: self
                .answer_easy(easy_interval.0, easy_interval.1, ctx)
                .into(),
            dynamic_desired_retentions: None,
            dynamic_desired_retention_enabled: false,
        }
    }

    pub(crate) fn failing_review_interval(
        self,
        ctx: &StateContext,
    ) -> (f32, i32, Option<FsrsMemoryState>) {
        if let Some(states) = &ctx.fsrs_next_states {
            // In FSRS, fuzz is applied when the card leaves the relearning
            // stage
            let (minimum, maximum) = ctx.min_and_max_review_intervals(ctx.minimum_lapse_interval);
            (
                states.again.interval.clamp(minimum as f32, maximum as f32),
                0,
                Some(states.again.memory.into()),
            )
        } else {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(ctx.minimum_lapse_interval);
            let (interval, fuzz_delta_days) = ctx.with_review_fuzz_and_delta(
                (self.scheduled_days as f32).max(1.0) * ctx.lapse_multiplier,
                minimum,
                maximum,
            );
            (interval as f32, fuzz_delta_days, None)
        }
    }

    fn answer_again(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let lapses = self.lapses + u32::from(ctx.count_review_lapse);
        let (scheduled_days, fuzz_delta_days, memory_state) = self.failing_review_interval(ctx);
        let stored_scheduled_days = scheduled_days.round().max(1.0) as u32;
        let leeched = ctx.count_review_lapse
            && leech_threshold_met(lapses, ctx.leech_threshold)
            && leech_young_enough(stored_scheduled_days, ctx);
        let again_review = ReviewState {
            scheduled_days: stored_scheduled_days,
            fuzz_delta_days,
            elapsed_days: 0,
            ease_factor: (self.ease_factor + EASE_FACTOR_AGAIN_DELTA).max(MINIMUM_EASE_FACTOR),
            lapses,
            leeched,
            memory_state,
        };
        let again_delay = if ctx.fsrs_uses_learning_queues() {
            ctx.relearn_steps.again_delay_secs_learn()
        } else {
            None
        };
        if let Some(again_delay) = again_delay {
            RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs: again_delay,
                    elapsed_secs: 0,
                    memory_state,
                },
                review: again_review,
            }
            .into()
        } else if let Some(ButtonInterval::Secs(scheduled_secs)) = interval {
            RelearnState {
                learning: LearnState {
                    remaining_steps: ctx.relearn_steps.remaining_for_failed(),
                    scheduled_secs,
                    elapsed_secs: 0,
                    memory_state,
                },
                review: again_review,
            }
            .into()
        } else {
            again_review.into()
        }
    }

    fn answer_hard(
        self,
        scheduled_days: u32,
        fuzz_delta_days: i32,
        ctx: &StateContext,
    ) -> ReviewState {
        ReviewState {
            scheduled_days,
            fuzz_delta_days,
            elapsed_days: 0,
            ease_factor: (self.ease_factor + EASE_FACTOR_HARD_DELTA).max(MINIMUM_EASE_FACTOR),
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into()),
            ..self
        }
    }

    fn answer_good(
        self,
        scheduled_days: u32,
        fuzz_delta_days: i32,
        ctx: &StateContext,
    ) -> ReviewState {
        ReviewState {
            scheduled_days,
            fuzz_delta_days,
            elapsed_days: 0,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.good.memory.into()),
            ..self
        }
    }

    fn answer_easy(
        self,
        scheduled_days: u32,
        fuzz_delta_days: i32,
        ctx: &StateContext,
    ) -> ReviewState {
        ReviewState {
            scheduled_days,
            fuzz_delta_days,
            elapsed_days: 0,
            ease_factor: self.ease_factor + EASE_FACTOR_EASY_DELTA,
            memory_state: ctx.fsrs_next_states.as_ref().map(|s| s.easy.memory.into()),
            ..self
        }
    }

    /// Return the intervals for hard, good and easy, each of which depends on
    /// the previous.
    /// SM-2 only; FSRS intervals go through `button_intervals`.
    fn passing_review_intervals(self, ctx: &StateContext) -> ((u32, i32), (u32, i32), (u32, i32)) {
        if self.days_late() < 0 {
            self.passing_early_review_intervals(ctx)
        } else {
            self.passing_nonearly_review_intervals(ctx)
        }
    }

    fn passing_nonearly_review_intervals(
        self,
        ctx: &StateContext,
    ) -> ((u32, i32), (u32, i32), (u32, i32)) {
        let current_interval = (self.scheduled_days as f32).max(1.0);
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
        let good_interval = constrain_passing_interval(
            ctx,
            (current_interval + days_late / 2.0) * self.ease_factor,
            if hard_factor <= 1.0 {
                self.scheduled_days + 1
            } else {
                hard_interval.0 + 1
            },
            true,
        );
        // easy
        let easy_interval = constrain_passing_interval(
            ctx,
            (current_interval + days_late) * self.ease_factor * ctx.easy_multiplier,
            good_interval.0 + 1,
            true,
        );

        (hard_interval, good_interval, easy_interval)
    }

    /// Mostly direct port from the Python version for now, so we can confirm
    /// implementation is correct.
    /// FIXME: this needs reworking in the future; it overly penalizes reviews
    /// done shortly before the due date.
    fn passing_early_review_intervals(
        self,
        ctx: &StateContext,
    ) -> ((u32, i32), (u32, i32), (u32, i32)) {
        let scheduled = (self.scheduled_days as f32).max(1.0);
        let elapsed = self.elapsed_days as f32;

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
        lapses >= threshold && (lapses - threshold).is_multiple_of(half_threshold)
    } else {
        false
    }
}

fn leech_young_enough(scheduled_days: u32, ctx: &StateContext) -> bool {
    if !ctx.leech_only_if_young {
        true
    } else if ctx.fsrs_next_states.is_some() {
        ctx.fsrs_again_s90
            .is_some_and(|stability| stability < YOUNG_LEECH_THRESHOLD_DAYS as f32)
    } else {
        scheduled_days < YOUNG_LEECH_THRESHOLD_DAYS
    }
}

/// Transform the provided hard/good/easy interval.
/// - Apply configured interval multiplier if not FSRS.
/// - Apply fuzz.
/// - Ensure it is at least `minimum`, and at least 1.
/// - Ensure it is at or below the configured maximum interval.
fn constrain_passing_interval(
    ctx: &StateContext,
    interval: f32,
    minimum: u32,
    fuzz: bool,
) -> (u32, i32) {
    let interval = if ctx.fsrs_next_states.is_some() {
        interval
    } else {
        interval * ctx.interval_multiplier
    };
    let (minimum, maximum) = ctx.min_and_max_review_intervals(minimum);
    if fuzz {
        ctx.with_review_fuzz_and_delta(interval, minimum, maximum)
    } else {
        ((interval.round() as u32).clamp(minimum, maximum), 0)
    }
}

#[cfg(test)]
mod test {
    use fsrs::ItemState;
    use fsrs::MemoryState;
    use fsrs::NextStates;

    use super::*;
    use crate::scheduler::states::steps::LearningSteps;
    use crate::scheduler::states::NormalState;

    #[test]
    fn again_does_not_retrigger_leech_without_new_lapse() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.count_review_lapse = false;
        let states = ReviewState {
            lapses: 8,
            ..Default::default()
        }
        .next_states(&ctx);

        assert_eq!(states.again.review_state().unwrap().lapses, 8);
        assert!(!states.again.leeched());
    }

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
    fn leech_only_if_young_uses_sm2_interval() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.leech_threshold = 2;
        ctx.leech_only_if_young = true;
        let state = ReviewState {
            scheduled_days: 100,
            elapsed_days: 100,
            lapses: 1,
            ..Default::default()
        };

        ctx.lapse_multiplier = 0.5;
        assert!(!state.answer_again(&ctx, None).leeched());

        ctx.lapse_multiplier = 0.1;
        assert!(state.answer_again(&ctx, None).leeched());
    }

    #[test]
    fn leech_only_if_young_uses_fsrs_stability() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.leech_threshold = 2;
        ctx.leech_only_if_young = true;
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(10.0),
            hard: fsrs_item_state(11.0),
            good: fsrs_item_state(12.0),
            easy: fsrs_item_state(13.0),
        });
        let state = ReviewState {
            scheduled_days: 100,
            elapsed_days: 100,
            lapses: 1,
            ..Default::default()
        };

        ctx.fsrs_again_s90 = Some(21.0);
        assert!(!state.answer_again(&ctx, None).leeched());

        ctx.fsrs_again_s90 = Some(20.99);
        assert!(state.answer_again(&ctx, None).leeched());
    }

    fn fsrs_item_state(interval: f32) -> ItemState {
        ItemState {
            interval,
            memory: MemoryState {
                stability: interval.max(0.1),
                difficulty: 5.0,
                stability_fast: interval.max(0.1),
            },
        }
    }

    fn fsrs_passing_days(state: ReviewState, ctx: &StateContext) -> (u32, u32, u32) {
        let next = state.next_states(ctx);
        let days = |state: CardState| match state {
            CardState::Normal(NormalState::Review(review)) => review.scheduled_days,
            other => panic!("expected a review state, got {other:?}"),
        };
        (days(next.hard), days(next.good), days(next.easy))
    }

    #[test]
    fn fsrs_again_respects_maximum_interval() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.maximum_review_interval = 5;
        ctx.minimum_lapse_interval = 1;
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(10.0),
            hard: fsrs_item_state(11.0),
            good: fsrs_item_state(12.0),
            easy: fsrs_item_state(13.0),
        });

        let state = ReviewState {
            scheduled_days: 30,
            elapsed_days: 30,
            memory_state: Some(crate::card::FsrsMemoryState {
                stability: 30.0,
                stability_internal: 30.0,
                stability_fast: None,
                difficulty: 5.0,
            }),
            ..Default::default()
        };

        ctx.relearn_steps = LearningSteps::new(&[]);
        let CardState::Normal(NormalState::Review(again)) = state.next_states(&ctx).again else {
            panic!("empty relearning steps should schedule Again directly as review");
        };
        assert_eq!(again.scheduled_days, 5);

        ctx.relearn_steps = LearningSteps::new(&[10.0]);
        let CardState::Normal(NormalState::Relearning(again)) = state.next_states(&ctx).again
        else {
            panic!("configured relearning steps should keep Again in relearning");
        };
        assert_eq!(again.review.scheduled_days, 5);
    }

    #[test]
    fn extreme_multiplier_fuzz() {
        let mut ctx = StateContext::defaults_for_testing();
        // our calculations should work correctly with a low ease or non-default
        // multiplier
        let state = ReviewState {
            scheduled_days: 1,
            fuzz_delta_days: 0,
            elapsed_days: 1,
            ease_factor: 1.3,
            lapses: 0,
            leeched: false,
            memory_state: None,
        };
        ctx.fuzz_factor = Some(0.0);
        assert_eq!(
            state.passing_review_intervals(&ctx),
            ((2, 0), (3, 0), (4, 0))
        );

        // this is a silly multiplier, but it shouldn't underflow
        ctx.interval_multiplier = 0.1;
        assert_eq!(
            state.passing_review_intervals(&ctx),
            ((2, 0), (3, 0), (4, 0))
        );
        ctx.fuzz_factor = Some(0.99);
        assert_eq!(
            state.passing_review_intervals(&ctx),
            ((2, 0), (4, 1), (6, 1))
        );

        // maximum must be respected no matter what
        ctx.interval_multiplier = 10.0;
        ctx.maximum_review_interval = 5;
        assert_eq!(
            state.passing_review_intervals(&ctx),
            ((5, 0), (5, 0), (5, 0))
        );
    }

    #[test]
    fn low_hard_multiplier_does_not_pull_good_down() {
        let mut ctx = StateContext::defaults_for_testing();
        // our calculations should work correctly with a low ease or non-default
        // multiplier
        ctx.hard_multiplier = 0.1;
        let state = ReviewState {
            scheduled_days: 2,
            fuzz_delta_days: 0,
            elapsed_days: 2,
            ease_factor: 1.3,
            lapses: 0,
            leeched: false,
            memory_state: None,
        };
        ctx.fuzz_factor = Some(0.0);
        assert_eq!(
            state.passing_review_intervals(&ctx),
            ((1, 0), (3, 0), (4, 0))
        );
    }

    #[test]
    fn fsrs_good_and_easy_preserve_previous_interval_within_fuzz_range() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.fuzz_factor = Some(0.0);
        let state = ReviewState {
            scheduled_days: 4,
            elapsed_days: 4,
            ..Default::default()
        };
        for (good, easy, expected_good, expected_easy) in
            [(2.7269483, 4.591988, 4, 5), (1.1, 2.7269483, 2, 4)]
        {
            ctx.fsrs_next_states = Some(NextStates {
                again: fsrs_item_state(1.0),
                hard: fsrs_item_state(1.0),
                good: fsrs_item_state(good),
                easy: fsrs_item_state(easy),
            });
            let (_, good, easy) = fsrs_passing_days(state, &ctx);
            assert_eq!(good, expected_good);
            assert_eq!(easy, expected_easy);
        }
    }

    #[test]
    fn fsrs_interval_protection_uses_collection_fuzz_and_respects_maximum() {
        use crate::scheduler::states::fuzz::ReviewFuzzConfig;

        let mut ctx = StateContext::defaults_for_testing();
        ctx.fuzz_factor = Some(0.0);
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(1.0),
            hard: fsrs_item_state(1.0),
            good: fsrs_item_state(3.0),
            easy: fsrs_item_state(3.0),
        });
        let state = ReviewState {
            scheduled_days: 6,
            elapsed_days: 6,
            ..Default::default()
        };
        // A genuine decrease outside the default fuzz range is allowed.
        assert!(fsrs_passing_days(state, &ctx).1 < 6);
        ctx.review_fuzz_config = ReviewFuzzConfig {
            base: 3.0,
            ..Default::default()
        };
        assert_eq!(fsrs_passing_days(state, &ctx).1, 6);
        ctx.maximum_review_interval = 3;
        let (_, good, easy) = fsrs_passing_days(state, &ctx);
        assert!(good <= 3);
        assert!(easy <= 3);
        ctx.maximum_review_interval = 36500;
        ctx.review_fuzz_config = ReviewFuzzConfig::none();
        assert_eq!(fsrs_passing_days(state, &ctx).1, 3);
    }
}
