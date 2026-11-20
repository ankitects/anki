// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::button_intervals::button_intervals;
use super::button_intervals::ButtonInterval;
use super::button_intervals::DayRule;
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
        let again_step = steps && ctx.steps.again_delay_secs_learn().is_some();
        let hard_step = steps && ctx.steps.hard_delay_secs(self.remaining_steps).is_some();
        let good_step = steps && ctx.steps.good_delay_secs(self.remaining_steps).is_some();
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
        remaining_steps: u32,
        interval: ButtonInterval,
        memory_state: Option<FsrsMemoryState>,
        ctx: &StateContext,
    ) -> CardState {
        match interval {
            ButtonInterval::Secs(scheduled_secs) => LearnState {
                remaining_steps,
                scheduled_secs,
                elapsed_secs: 0,
                memory_state,
            }
            .into(),
            ButtonInterval::Days {
                days,
                fuzz_delta_days,
            } => ReviewState {
                scheduled_days: days,
                fuzz_delta_days,
                ease_factor: ctx.initial_ease_factor,
                memory_state,
                ..Default::default()
            }
            .into(),
        }
    }

    fn graduate_sm2(ctx: &StateContext, interval: u32) -> CardState {
        let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
        let (scheduled_days, fuzz_delta_days) =
            ctx.with_review_fuzz_and_delta(interval.max(1) as f32, minimum, maximum);
        ReviewState {
            scheduled_days,
            fuzz_delta_days,
            ease_factor: ctx.initial_ease_factor,
            ..Default::default()
        }
        .into()
    }

    fn answer_again(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.again.memory.into());
        if ctx.fsrs_uses_learning_queues() {
            if let Some(again_delay) = ctx.steps.again_delay_secs_learn() {
                return LearnState {
                    remaining_steps: ctx.steps.remaining_for_failed(),
                    scheduled_secs: again_delay,
                    elapsed_secs: 0,
                    memory_state,
                }
                .into();
            }
        }
        match interval {
            Some(interval) => Self::graduate(
                ctx.steps.remaining_for_failed(),
                interval,
                memory_state,
                ctx,
            ),
            None => Self::graduate_sm2(ctx, ctx.graduating_interval_good),
        }
    }

    fn answer_hard(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into());
        if ctx.fsrs_uses_learning_queues() {
            if let Some(hard_delay) = ctx.steps.hard_delay_secs(self.remaining_steps) {
                return LearnState {
                    scheduled_secs: hard_delay,
                    elapsed_secs: 0,
                    memory_state,
                    ..self
                }
                .into();
            }
        }
        match interval {
            Some(interval) => Self::graduate(0, interval, memory_state, ctx),
            None => Self::graduate_sm2(ctx, ctx.graduating_interval_good),
        }
    }

    fn answer_good(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.good.memory.into());
        if ctx.fsrs_uses_learning_queues() {
            if let Some(good_delay) = ctx.steps.good_delay_secs(self.remaining_steps) {
                return LearnState {
                    remaining_steps: ctx.steps.remaining_for_good(self.remaining_steps),
                    scheduled_secs: good_delay,
                    elapsed_secs: 0,
                    memory_state,
                }
                .into();
            }
        }
        match interval {
            Some(interval) => Self::graduate(0, interval, memory_state, ctx),
            None => Self::graduate_sm2(ctx, ctx.graduating_interval_good),
        }
    }

    fn answer_easy(self, ctx: &StateContext, interval: Option<ButtonInterval>) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.easy.memory.into());
        match interval {
            Some(interval) => Self::graduate(0, interval, memory_state, ctx),
            None => Self::graduate_sm2(ctx, ctx.graduating_interval_easy),
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
    fn fsrs_short_term_intervals_are_at_least_one_second() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.steps = LearningSteps::new(&[]);
        ctx.fsrs_allow_short_term = true;
        ctx.fsrs_short_term_with_steps_enabled = true;
        ctx.fsrs_minimum_interval_secs = 5;
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(0.000001),
            hard: fsrs_item_state(0.000001),
            good: fsrs_item_state(0.000001),
            easy: fsrs_item_state(1.0),
        });

        let state = LearnState {
            remaining_steps: 0,
            scheduled_secs: 0,
            elapsed_secs: 0,
            memory_state: None,
        };
        let next = state.next_states(&ctx);

        let CardState::Normal(super::super::NormalState::Learning(again)) = next.again else {
            panic!("Again should stay in learning");
        };
        assert_eq!(again.scheduled_secs, 5);

        let CardState::Normal(super::super::NormalState::Learning(hard)) = next.hard else {
            panic!("Hard should stay in learning");
        };
        assert_eq!(hard.scheduled_secs, 5);

        let CardState::Normal(super::super::NormalState::Learning(good)) = next.good else {
            panic!("Good should stay in learning");
        };
        assert_eq!(good.scheduled_secs, 5);
    }

    #[test]
    fn fsrs_short_term_can_follow_configured_learning_steps() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.steps = LearningSteps::new(&[1.0]);
        ctx.fsrs_allow_short_term = true;
        ctx.fsrs_short_term_with_steps_enabled = true;
        ctx.fsrs_minimum_interval_secs = 5;
        ctx.fsrs_next_states = Some(NextStates {
            again: fsrs_item_state(0.000001),
            hard: fsrs_item_state(0.000001),
            good: fsrs_item_state(0.000001),
            easy: fsrs_item_state(1.0),
        });

        let state = LearnState {
            remaining_steps: 1,
            scheduled_secs: 60,
            elapsed_secs: 0,
            memory_state: None,
        };
        let next = state.next_states(&ctx);

        let CardState::Normal(super::super::NormalState::Learning(good)) = next.good else {
            panic!("Good should stay in short-term learning after final configured step");
        };
        assert_eq!(good.remaining_steps, 0);
        assert_eq!(good.scheduled_secs, 5);

        let followup = good.next_states(&ctx);
        let CardState::Normal(super::super::NormalState::Learning(hard)) = followup.hard else {
            panic!("Hard should stay in FSRS short-term learning");
        };
        assert_eq!(hard.remaining_steps, 0);
        assert_eq!(hard.scheduled_secs, 5);
    }
}

#[cfg(test)]
mod upstream_tests {
    use super::super::steps::LearningSteps;
    use super::*;
    use crate::scheduler::states::NormalState;

    // defaults_for_testing: steps=[1.0, 10.0]min, graduating_good=1day,
    // graduating_easy=4days, fuzz_factor=None (deterministic)
    //   remaining_steps=2 → first step  (1min = 60s)
    //   remaining_steps=1 → last step  (10min = 600s)

    fn learn_state(remaining_steps: u32) -> LearnState {
        LearnState {
            remaining_steps,
            scheduled_secs: 60,
            elapsed_secs: 0,
            memory_state: None,
        }
    }

    #[test]
    fn again_on_any_step_resets_to_first_step() {
        // steps [1.0, 10.0]min: remaining=1 means last step (10min)
        // Again must jump all the way back to step 1 (1min), not just go back one.
        let ctx = StateContext::defaults_for_testing();
        for remaining in [1, 2] {
            // remaining_steps = 1 or 2
            let state = learn_state(remaining);
            let states = state.next_states(&ctx);
            assert!(matches!(
                states.again,
                CardState::Normal(NormalState::Learning(LearnState {
                    remaining_steps: 2, // reset to step 1 (1min), not stay at step 2 (10min)
                    scheduled_secs: 60,
                    ..
                }))
            ));
        }
    }

    #[test]
    fn again_with_no_steps_graduates_to_review() {
        let ctx = StateContext {
            steps: LearningSteps::new(&[]),
            ..StateContext::defaults_for_testing()
        };
        assert_eq!(
            ctx.steps.remaining_for_failed(),
            0,
            "precondition: no steps configured"
        );
        // remaining_steps is irrelevant here: with no steps configured,
        // again_delay_secs_learn() always returns None regardless of position.
        let state = learn_state(0);
        let states = state.next_states(&ctx);
        // No steps → Again graduates directly to Review using graduating_interval_good
        // (1 day)
        assert!(matches!(
            states.again,
            CardState::Normal(NormalState::Review(ReviewState {
                scheduled_days: 1,
                ..
            }))
        ));
    }

    #[test]
    fn hard_any_step_stays_on_same_step() {
        let ctx = StateContext::defaults_for_testing();

        // On first step (remaining=2): hard delay = avg(60+600)/2 = 330s, stays at step
        // 1
        let state = learn_state(2); // first step (means step 1 of 2)
        let states = state.next_states(&ctx);
        assert!(matches!(
            states.hard,
            CardState::Normal(NormalState::Learning(LearnState {
                remaining_steps: 2, // stays on step 1
                scheduled_secs: 330,
                ..
            }))
        ));

        // On last step (remaining=1): hard delay = 600s (same step), stays at step 2
        let state = learn_state(1); // last step (means step 2 of 2)
        let states = state.next_states(&ctx);
        assert!(matches!(
            states.hard,
            CardState::Normal(NormalState::Learning(LearnState {
                remaining_steps: 1, // stays on step 2
                scheduled_secs: 600,
                ..
            }))
        ));
    }

    #[test]
    fn hard_with_no_steps_graduates_to_review() {
        let ctx = StateContext {
            steps: LearningSteps::new(&[]),
            ..StateContext::defaults_for_testing()
        };
        assert_eq!(
            ctx.steps.remaining_for_failed(),
            0,
            "precondition: no steps configured"
        );
        // remaining_steps is irrelevant: hard_delay_secs() returns None whenever steps
        // is empty.
        let state = learn_state(1);
        let states = state.next_states(&ctx);
        // No steps → Hard graduates to Review using graduating_interval_good (1 day)
        assert!(matches!(
            states.hard,
            CardState::Normal(NormalState::Review(ReviewState {
                scheduled_days: 1,
                ..
            }))
        ));
    }

    #[test]
    fn good_on_first_step_advances_to_next_step() {
        let ctx = StateContext::defaults_for_testing();
        let state = learn_state(2); // first step (means step 1 of 2)
        let states = state.next_states(&ctx);
        // Good advances remaining_steps from 2 to 1, next delay = 600s (10min)
        assert!(matches!(
            states.good,
            CardState::Normal(NormalState::Learning(LearnState {
                remaining_steps: 1, // advances to step 2 of 2
                scheduled_secs: 600,
                ..
            }))
        ));
    }

    #[test]
    fn good_on_last_step_advances_to_review() {
        let ctx = StateContext::defaults_for_testing();
        let state = learn_state(1); // last step (means step 2 of 2)
        let states = state.next_states(&ctx);
        // Good from last step → Review with graduating_interval_good = 1 day
        assert!(matches!(
            states.good,
            CardState::Normal(NormalState::Review(ReviewState {
                scheduled_days: 1,
                ..
            }))
        ));
    }

    #[test]
    fn easy_always_graduates_to_review() {
        let ctx = StateContext::defaults_for_testing();
        for remaining in [1, 2] {
            // remaining_steps = 1 or 2
            let state = learn_state(remaining);
            let states = state.next_states(&ctx);
            assert!(
                matches!(states.easy, CardState::Normal(NormalState::Review(_))),
                "Easy with remaining_steps={remaining} should always graduate to Review"
            );
        }
    }

    #[test]
    fn easy_interval_is_longer_than_good() {
        let ctx = StateContext::defaults_for_testing();
        let state = learn_state(1); // last step so Good also graduates
        let states = state.next_states(&ctx);
        let easy_days = match states.easy {
            CardState::Normal(NormalState::Review(r)) => r.scheduled_days,
            _ => panic!("easy should produce a ReviewState"),
        };
        let good_days = match states.good {
            CardState::Normal(NormalState::Review(r)) => r.scheduled_days,
            _ => panic!("good from last step should produce a ReviewState"),
        };
        // graduating_interval_easy=4 > graduating_interval_good=1
        assert!(
            easy_days > good_days,
            "easy interval ({easy_days}d) should exceed good interval ({good_days}d)"
        );
    }
}
