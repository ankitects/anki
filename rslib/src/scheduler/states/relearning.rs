// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::button_intervals::button_intervals;
use super::button_intervals::ButtonInterval;
use super::button_intervals::DayRule;
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
        let intervals = self.fsrs_button_intervals(ctx);
        SchedulingStates {
            current: self.into(),
            again: self.answer_again(ctx, intervals[0]),
            hard: self.answer_hard(ctx, intervals[1]),
            good: self.answer_good(ctx, intervals[2]),
            easy: self.answer_easy(ctx, intervals[3]),
            dynamic_desired_retentions: None,
            dynamic_desired_retention_enabled: false,
        }
    }

    fn fsrs_button_intervals(self, ctx: &StateContext) -> [Option<ButtonInterval>; 4] {
        let Some(states) = &ctx.fsrs_next_states else {
            return [None; 4];
        };
        let steps = ctx.fsrs_uses_learning_queues();
        let remaining = self.learning.remaining_steps;
        let again_step = steps && ctx.relearn_steps.again_delay_secs_learn().is_some();
        let hard_step = steps && ctx.relearn_steps.hard_delay_secs(remaining).is_some();
        let good_step = steps && ctx.relearn_steps.good_delay_secs(remaining).is_some();
        button_intervals(
            ctx,
            [
                (!again_step).then_some(states.again.interval),
                (!hard_step).then_some(states.hard.interval),
                (!good_step).then_some(states.good.interval),
                Some(states.easy.interval),
            ],
            DayRule::Graduating,
        )
    }

    fn graduate(
        self,
        remaining_steps: u32,
        elapsed_secs: u32,
        interval: ButtonInterval,
        review: ReviewState,
    ) -> CardState {
        match interval {
            ButtonInterval::Secs(scheduled_secs) => RelearnState {
                learning: LearnState {
                    remaining_steps,
                    scheduled_secs,
                    elapsed_secs,
                    memory_state: review.memory_state,
                },
                review: ReviewState {
                    scheduled_days: 1,
                    fuzz_delta_days: 0,
                    ..review
                },
            }
            .into(),
            ButtonInterval::Days {
                days,
                fuzz_delta_days,
            } => ReviewState {
                scheduled_days: days,
                fuzz_delta_days,
                ..review
            }
            .into(),
        }
    }

    fn answer_again(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let (scheduled_days, fuzz_delta_days, memory_state) =
            self.review.failing_review_interval(ctx);
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
                review: ReviewState {
                    scheduled_days: scheduled_days.round().max(1.0) as u32,
                    fuzz_delta_days,
                    elapsed_days: 0,
                    memory_state,
                    ..self.review
                },
            }
            .into()
        } else if let Some(interval) = interval {
            self.graduate(
                ctx.relearn_steps.remaining_for_failed(),
                0,
                interval,
                ReviewState {
                    memory_state,
                    ..self.review
                },
            )
        } else {
            self.review.into()
        }
    }

    fn answer_hard(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into());
        let hard_delay = if ctx.fsrs_uses_learning_queues() {
            ctx.relearn_steps
                .hard_delay_secs(self.learning.remaining_steps)
        } else {
            None
        };
        if let Some(hard_delay) = hard_delay {
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
        } else if let Some(interval) = interval {
            self.graduate(
                0,
                self.learning.elapsed_secs,
                interval,
                ReviewState {
                    memory_state,
                    ..self.review
                },
            )
        } else {
            self.review.into()
        }
    }

    fn answer_good(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.good.memory.into());
        let good_delay = if ctx.fsrs_uses_learning_queues() {
            ctx.relearn_steps
                .good_delay_secs(self.learning.remaining_steps)
        } else {
            None
        };
        if let Some(good_delay) = good_delay {
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
        } else if let Some(interval) = interval {
            self.graduate(
                0,
                self.learning.elapsed_secs,
                interval,
                ReviewState {
                    memory_state,
                    ..self.review
                },
            )
        } else {
            self.review.into()
        }
    }

    fn answer_easy(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.easy.memory.into());
        match interval {
            Some(interval) => self.graduate(
                0,
                0,
                interval,
                ReviewState {
                    elapsed_days: 0,
                    memory_state,
                    ..self.review
                },
            ),
            None => ReviewState {
                scheduled_days: self.review.scheduled_days + 1,
                fuzz_delta_days: 0,
                elapsed_days: 0,
                memory_state,
                ..self.review
            }
            .into(),
        }
    }
}

#[cfg(test)]
mod test {
    use fsrs::ItemState;
    use fsrs::MemoryState;
    use fsrs::NextStates;

    use super::*;
    use crate::scheduler::states::steps::LearningSteps;

    fn fsrs_item_state(interval: f32) -> ItemState {
        ItemState {
            interval,
            memory: MemoryState {
                stability: 0.1,
                difficulty: 5.0,
                stability_fast: 0.1,
            },
        }
    }

    #[test]
    fn fsrs_short_term_can_follow_configured_relearning_steps() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.relearn_steps = LearningSteps::new(&[10.0]);
        ctx.fsrs_allow_short_term = true;
        ctx.fsrs_short_term_with_steps_enabled = true;
        ctx.fsrs_minimum_interval_secs = 5;
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(0.000001),
            hard: fsrs_item_state(0.000001),
            good: fsrs_item_state(0.000001),
            easy: fsrs_item_state(1.0),
        });

        let state = RelearnState {
            learning: LearnState {
                remaining_steps: 1,
                scheduled_secs: 600,
                elapsed_secs: 0,
                memory_state: None,
            },
            review: ReviewState {
                scheduled_days: 1,
                elapsed_days: 1,
                ..Default::default()
            },
        };
        let next = state.next_states(&ctx);

        let CardState::Normal(super::super::NormalState::Relearning(good)) = next.good else {
            panic!("Good should stay in short-term relearning after final configured step");
        };
        assert_eq!(good.learning.remaining_steps, 0);
        assert_eq!(good.learning.scheduled_secs, 5);

        let followup = good.next_states(&ctx);
        let CardState::Normal(super::super::NormalState::Relearning(hard)) = followup.hard else {
            panic!("Hard should stay in FSRS short-term relearning");
        };
        assert_eq!(hard.learning.remaining_steps, 0);
        assert_eq!(hard.learning.scheduled_secs, 5);
    }
}

#[cfg(test)]
mod upstream_tests {
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
            stability_fast: 0.0,
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
                fuzz_delta_days: 0,
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
    fn hard_stays_in_relearning_and_keeps_same_step() {
        let ctx = StateContext::defaults_for_testing(); // relearn_steps=[10.0], remaining=1
        let state = relearn_state();
        let states = state.next_states(&ctx);
        let CardState::Normal(NormalState::Relearning(r)) = states.hard else {
            panic!("hard should produce a RelearnState, got: {:?}", states.hard);
        };
        assert_eq!(
            r.learning.remaining_steps, 1,
            "hard must keep current step (remaining=1), not reset or advance"
        );
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
            fsrs_short_term_with_steps_enabled: true,
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
            fsrs_short_term_with_steps_enabled: true,
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
            fsrs_short_term_with_steps_enabled: true,
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

    // Multi-step relearning tests.
    // Two relearn steps [5.0, 10.0]min: remaining=2 → first step, remaining=1 →
    // last step.

    fn relearn_state_at_last_of_two_steps() -> RelearnState {
        RelearnState {
            learning: LearnState {
                remaining_steps: 1, // at the last step of two
                scheduled_secs: 600,
                elapsed_secs: 0,
                memory_state: None,
            },
            review: ReviewState {
                scheduled_days: 3,
                fuzz_delta_days: 0,
                elapsed_days: 3,
                ease_factor: 2.5,
                lapses: 1,
                leeched: false,
                memory_state: None,
            },
        }
    }

    #[test]
    fn again_with_two_steps_resets_to_first_step() {
        // Card is at the last step (remaining=1). Again should jump back to the
        // first step (remaining=2), not stay at remaining=1.
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[5.0, 10.0]),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state_at_last_of_two_steps();
        let states = state.next_states(&ctx);
        let CardState::Normal(NormalState::Relearning(r)) = states.again else {
            panic!(
                "again should produce a RelearnState, got: {:?}",
                states.again
            );
        };
        assert_eq!(
            r.learning.remaining_steps, 2,
            "again must reset to first step (remaining=2), not stay at remaining=1"
        );
    }

    #[test]
    fn hard_with_two_steps_keeps_same_remaining_steps() {
        // Hard should keep the card on the current step without advancing or resetting.
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[5.0, 10.0]),
            ..StateContext::defaults_for_testing()
        };
        let state = relearn_state_at_last_of_two_steps(); // remaining=1
        let states = state.next_states(&ctx);
        let CardState::Normal(NormalState::Relearning(r)) = states.hard else {
            panic!("hard should produce a RelearnState, got: {:?}", states.hard);
        };
        assert_eq!(
            r.learning.remaining_steps, 1,
            "hard must keep current step (remaining=1), not reset or advance"
        );
    }

    #[test]
    fn good_from_non_last_step_stays_in_relearning() {
        // Card is at remaining=2 (first of two steps). Good advances to next step
        // but should NOT graduate to Review yet.
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[5.0, 10.0]),
            ..StateContext::defaults_for_testing()
        };
        let state = RelearnState {
            learning: LearnState {
                remaining_steps: 2,
                ..relearn_state_at_last_of_two_steps().learning
            },
            ..relearn_state_at_last_of_two_steps()
        };
        let states = state.next_states(&ctx);
        assert!(
            matches!(states.good, CardState::Normal(NormalState::Relearning(_))),
            "good from non-last step must stay in Relearning, got: {:?}",
            states.good
        );
    }

    #[test]
    fn good_from_non_last_step_advances_remaining_steps() {
        // Good must decrease remaining_steps by 1, not reset it.
        let ctx = StateContext {
            relearn_steps: LearningSteps::new(&[5.0, 10.0]),
            ..StateContext::defaults_for_testing()
        };
        let state = RelearnState {
            learning: LearnState {
                remaining_steps: 2,
                ..relearn_state_at_last_of_two_steps().learning
            },
            ..relearn_state_at_last_of_two_steps()
        };
        let states = state.next_states(&ctx);
        let CardState::Normal(NormalState::Relearning(r)) = states.good else {
            panic!("good should produce a RelearnState, got: {:?}", states.good);
        };
        assert_eq!(
            r.learning.remaining_steps, 1,
            "good must advance remaining_steps from 2 to 1, not reset or stay"
        );
    }
}
