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
            again: self.answer_again(ctx),
            hard: self.answer_hard(ctx),
            good: self.answer_good(ctx),
            easy: self.answer_easy(ctx).into(),
        }
    }

    fn answer_again(self, ctx: &StateContext) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.again.memory.into());
        if let Some(again_delay) = ctx.steps.again_delay_secs_learn() {
            LearnState {
                remaining_steps: ctx.steps.remaining_for_failed(),
                scheduled_secs: again_delay,
                elapsed_secs: 0,
                memory_state,
            }
            .into()
        } else {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let (interval, short_term) = if let Some(states) = &ctx.fsrs_next_states {
                (
                    states.again.interval,
                    ctx.fsrs_allow_short_term
                        && (ctx.fsrs_short_term_with_steps_enabled || ctx.steps.is_empty())
                        && states.again.interval < 0.5,
                )
            } else {
                (ctx.graduating_interval_good as f32, false)
            };

            if short_term {
                LearnState {
                    remaining_steps: ctx.steps.remaining_for_failed(),
                    scheduled_secs: (interval * 86_400.0) as u32,
                    elapsed_secs: 0,
                    memory_state,
                }
                .into()
            } else {
                ReviewState {
                    scheduled_days: ctx.with_review_fuzz(
                        interval.round().max(1.0),
                        minimum,
                        maximum,
                    ),
                    ease_factor: ctx.initial_ease_factor,
                    memory_state,
                    ..Default::default()
                }
                .into()
            }
        }
    }

    fn answer_hard(self, ctx: &StateContext) -> CardState {
        let memory_state = ctx.fsrs_next_states.as_ref().map(|s| s.hard.memory.into());
        if let Some(hard_delay) = ctx.steps.hard_delay_secs(self.remaining_steps) {
            LearnState {
                scheduled_secs: hard_delay,
                elapsed_secs: 0,
                memory_state,
                ..self
            }
            .into()
        } else {
            let (minimum, maximum) = ctx.min_and_max_review_intervals(1);
            let (interval, short_term) = if let Some(states) = &ctx.fsrs_next_states {
                (
                    states.hard.interval,
                    ctx.fsrs_allow_short_term
                        && (ctx.fsrs_short_term_with_steps_enabled || ctx.steps.is_empty())
                        && states.hard.interval < 0.5,
                )
            } else {
                (ctx.graduating_interval_good as f32, false)
            };

            if short_term {
                LearnState {
                    scheduled_secs: (interval * 86_400.0) as u32,
                    elapsed_secs: 0,
                    memory_state,
                    ..self
                }
                .into()
            } else {
                ReviewState {
                    scheduled_days: ctx.with_review_fuzz(
                        interval.round().max(1.0),
                        minimum,
                        maximum,
                    ),
                    ease_factor: ctx.initial_ease_factor,
                    memory_state,
                    ..Default::default()
                }
                .into()
            }
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
            let (interval, short_term) = if let Some(states) = &ctx.fsrs_next_states {
                (
                    states.good.interval,
                    ctx.fsrs_allow_short_term
                        && (ctx.fsrs_short_term_with_steps_enabled || ctx.steps.is_empty())
                        && states.good.interval < 0.5,
                )
            } else {
                (ctx.graduating_interval_good as f32, false)
            };

            if short_term {
                LearnState {
                    scheduled_secs: (interval * 86_400.0) as u32,
                    elapsed_secs: 0,
                    memory_state,
                    ..self
                }
                .into()
            } else {
                ReviewState {
                    scheduled_days: ctx.with_review_fuzz(
                        interval.round().max(1.0),
                        minimum,
                        maximum,
                    ),
                    ease_factor: ctx.initial_ease_factor,
                    memory_state,
                    ..Default::default()
                }
                .into()
            }
        }
    }

    fn answer_easy(self, ctx: &StateContext) -> ReviewState {
        let (mut minimum, maximum) = ctx.min_and_max_review_intervals(1);
        let interval = if let Some(states) = &ctx.fsrs_next_states {
            let good = ctx.with_review_fuzz(states.good.interval, minimum, maximum);
            minimum = good + 1;
            states.easy.interval.round().max(1.0) as u32
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

#[cfg(test)]
mod tests {
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
        for remaining in [1, 2] { // remaining_steps = 1 or 2
            let state = learn_state(remaining);
            let states = state.next_states(&ctx);
            assert!(matches!(
                states.again,
                CardState::Normal(NormalState::Learning(LearnState {
                    remaining_steps: 2,  // reset to step 1 (1min), not stay at step 2 (10min)
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
        assert!(ctx.steps.is_empty(), "precondition: no steps configured");
        // remaining_steps is irrelevant here: with no steps configured,
        // again_delay_secs_learn() always returns None regardless of position.
        let state = learn_state(0);
        let states = state.next_states(&ctx);
        // No steps → Again graduates directly to Review using graduating_interval_good (1 day)
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

        // On first step (remaining=2): hard delay = avg(60+600)/2 = 330s, stays at step 1
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
        assert!(ctx.steps.is_empty(), "precondition: no steps configured");
        // remaining_steps is irrelevant: hard_delay_secs() returns None whenever steps is empty.
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
        for remaining in [1, 2] { // remaining_steps = 1 or 2
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
