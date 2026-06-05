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
            if ctx.fsrs_allow_short_term
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
            if ctx.fsrs_allow_short_term
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
            if ctx.fsrs_allow_short_term
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

#[cfg(test)]
mod tests {
    use fsrs::ItemState;
    use fsrs::MemoryState;
    use fsrs::NextStates as FsrsNextStates;

    use super::super::steps::LearningSteps;
    use super::*;
    use crate::scheduler::states::NormalState;

    // defaults_for_testing: relearn_steps=[10.0min=600s], lapse_multiplier=0.0,
    // minimum_lapse_interval=1, fuzz_factor=None (deterministic)
    //   remaining_steps=1 → only relearn step (10min = 600s)
    //   hard delay for single step = 150% of 600 = 900s
    //   good_delay_secs(1) = None → only 1 step, no next step

    fn fsrs_states(again: f32, hard: f32, good: f32, easy: f32) -> FsrsNextStates {
        let mem = MemoryState {
            stability: 4.0,
            difficulty: 5.0,
        };
        FsrsNextStates {
            again: ItemState {
                memory: mem,
                interval: again,
            },
            hard: ItemState {
                memory: mem,
                interval: hard,
            },
            good: ItemState {
                memory: mem,
                interval: good,
            },
            easy: ItemState {
                memory: mem,
                interval: easy,
            },
        }
    }

    fn relearn_state() -> RelearnState {
        RelearnState {
            learning: LearnState {
                remaining_steps: 1,
                scheduled_secs: 600,
                elapsed_secs: 0,
                memory_state: None,
            },
            review: ReviewState {
                scheduled_days: 3,
                elapsed_days: 3,
                ease_factor: 2.5,
                lapses: 1,
                leeched: false,
                memory_state: None,
            },
        }
    }

    #[test]
    fn again_stays_in_relearning() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // Again with active relearn steps → stays in RelearnState
        assert!(matches!(
            states.again,
            CardState::Normal(NormalState::Relearning(_))
        ));
    }

    #[test]
    fn again_applies_lapse_penalty_to_review_interval() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state(); // review.scheduled_days = 3
        let current_scheduled_days = state.review.scheduled_days;
        let states = state.next_states(&ctx);
        // failing_review_interval: 3 * lapse_multiplier(0.0) = 0, clamped to
        // minimum_lapse_interval(1) → 1
        assert_eq!(current_scheduled_days, 3);

        let CardState::Normal(NormalState::Relearning(relearn)) = states.again else {
            panic!(
                "again should produce a RelearnState, got: {:?}",
                states.again
            );
        };

        assert_eq!(
            relearn.review.scheduled_days, 1,
            "lapse penalty should reduce scheduled_days from 3 to 1"
        );
    }

    #[test]
    fn again_with_no_steps_exits_to_review() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // No relearn steps + no FSRS → exit relearning (self.review.into())
        assert!(matches!(
            states.again,
            CardState::Normal(NormalState::Review(_))
        ));
    }

    #[test]
    fn hard_stays_in_relearning() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // Hard with active relearn steps → stays in RelearnState
        // single-step hard delay = 150% of 600s = 900s
        assert!(matches!(
            states.hard,
            CardState::Normal(NormalState::Relearning(_))
        ));
    }

    #[test]
    fn hard_with_no_steps_exits_to_review() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // No relearn steps + no FSRS → exit relearning
        assert!(matches!(
            states.hard,
            CardState::Normal(NormalState::Review(_))
        ));
    }

    #[test]
    fn good_from_last_step_graduates_to_review() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state(); // remaining_steps=1, only 1 relearn step → no next step
        let states = state.next_states(&ctx);
        // Good from last (only) relearn step → graduate to Review (self.review.into())
        assert!(matches!(
            states.good,
            CardState::Normal(NormalState::Review(_))
        ));
    }

    #[test]
    fn easy_always_graduates_to_review() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state();
        let states = state.next_states(&ctx);
        assert!(matches!(
            states.easy,
            CardState::Normal(NormalState::Review(_))
        ));
    }

    #[test]
    fn easy_interval_exceeds_current_scheduled_days() {
        let ctx = StateContext::defaults_for_testing();
        let state = relearn_state(); // review.scheduled_days = 3
        let states = state.next_states(&ctx);
        // SM-2: easy = scheduled_days + 1 = 4
        if let CardState::Normal(NormalState::Review(r)) = states.easy {
            assert!(
                r.scheduled_days > state.review.scheduled_days,
                "easy interval ({}d) should exceed current scheduled_days ({}d)",
                r.scheduled_days,
                state.review.scheduled_days
            );
            assert_eq!(r.scheduled_days, 4);
        } else {
            panic!("easy should produce a ReviewState");
        }
    }

    #[test]
    fn again_fsrs_exits_to_review_with_algorithm_interval() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(2.0, 3.0, 5.0, 7.0)),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // SM-2 without steps gives self.review (scheduled_days=3); FSRS gives
        // again.interval=2
        let CardState::Normal(NormalState::Review(r)) = states.again else {
            panic!("expected Review, got: {:?}", states.again);
        };
        assert_eq!(
            r.scheduled_days, 2,
            "FSRS again should use algorithm interval (2d)"
        );
    }

    #[test]
    fn again_fsrs_short_term_stays_in_relearning() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(0.2, 0.3, 0.4, 7.0)),
            fsrs_allow_short_term: true,
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // interval=0.2 < 0.5, fsrs_allow_short_term=true, relearn_steps empty
        // → stays in Relearning with scheduled_secs = (0.2 * 86_400.0) as u32 = 17280
        let CardState::Normal(NormalState::Relearning(r)) = states.again else {
            panic!("expected Relearning, got: {:?}", states.again);
        };
        assert_eq!(r.learning.scheduled_secs, 17280);
    }

    #[test]
    fn hard_fsrs_exits_to_review_with_algorithm_interval() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(2.0, 3.0, 5.0, 7.0)),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // SM-2 without steps gives self.review (scheduled_days=3); FSRS gives
        // hard.interval=3
        let CardState::Normal(NormalState::Review(r)) = states.hard else {
            panic!("expected Review, got: {:?}", states.hard);
        };
        assert_eq!(
            r.scheduled_days, 3,
            "FSRS hard should use algorithm interval (3d)"
        );
    }

    #[test]
    fn hard_fsrs_short_term_stays_in_relearning() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(0.2, 0.3, 0.4, 7.0)),
            fsrs_allow_short_term: true,
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // interval=0.3 < 0.5, fsrs_allow_short_term=true, relearn_steps empty
        // → stays in Relearning with scheduled_secs = (0.3 * 86_400.0) as u32 = 25920
        let CardState::Normal(NormalState::Relearning(r)) = states.hard else {
            panic!("expected Relearning, got: {:?}", states.hard);
        };
        assert_eq!(r.learning.scheduled_secs, 25920);
    }

    #[test]
    fn good_fsrs_exits_to_review_with_algorithm_interval() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(2.0, 3.0, 5.0, 7.0)),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // SM-2 without steps gives self.review (scheduled_days=3); FSRS gives
        // good.interval=5
        let CardState::Normal(NormalState::Review(r)) = states.good else {
            panic!("expected Review, got: {:?}", states.good);
        };
        assert_eq!(
            r.scheduled_days, 5,
            "FSRS good should use algorithm interval (5d)"
        );
    }

    #[test]
    fn good_fsrs_short_term_stays_in_relearning() {
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[]),
            fsrs_next_states: Some(fsrs_states(0.2, 0.3, 0.4, 7.0)),
            fsrs_allow_short_term: true,
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state();
        let states = state.next_states(&ctx);
        // interval=0.4 < 0.5, fsrs_allow_short_term=true, relearn_steps empty
        // → stays in Relearning with scheduled_secs = (0.4 * 86_400.0) as u32 = 34560
        let CardState::Normal(NormalState::Relearning(r)) = states.good else {
            panic!("expected Relearning, got: {:?}", states.good);
        };
        assert_eq!(r.learning.scheduled_secs, 34560);
    }

    #[test]
    fn easy_fsrs_uses_algorithm_interval_not_plus_one() {
        let ctx = StateContext {
            // easy.interval=7: clamped above good(3)+1=4 minimum → 7 days
            fsrs_next_states: Some(fsrs_states(1.0, 2.0, 3.0, 7.0)),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state(); // review.scheduled_days = 3
        let states = state.next_states(&ctx);
        // SM-2 would give 3 + 1 = 4 days; FSRS should give easy.interval = 7 days
        let CardState::Normal(NormalState::Review(r)) = states.easy else {
            panic!("easy should produce a ReviewState, got: {:?}", states.easy);
        };
        assert_eq!(
            r.scheduled_days, 7,
            "FSRS easy should use algorithm interval (7d), not scheduled_days+1 (4d)"
        );
    }
}
