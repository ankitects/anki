// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Convert FSRS7's fractional intervals only after evaluating all answer
//! buttons. Configured steps take precedence. Model intervals below twelve
//! hours use the existing learning queues; whole-day intervals retain upstream
//! fuzz and limits.

use super::fuzz::minimum_review_fuzz_interval;
use super::*;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) enum ButtonInterval {
    Secs(u32),
    Days(u32),
}

pub(super) fn button_intervals(
    ctx: &StateContext,
    intervals: [Option<f32>; 4],
    previous_review_interval: Option<u32>,
) -> [Option<ButtonInterval>; 4] {
    let mut previous_secs = 0;
    let mut previous_days = 0;
    std::array::from_fn(|index| {
        let interval = intervals[index]?;
        if interval < 0.5 {
            let secs = ((interval * 86_400.0).round() as u32)
                .max(1)
                .max(previous_secs);
            previous_secs = secs;
            Some(ButtonInterval::Secs(secs))
        } else {
            let floor = previous_days + 1;
            let days = if index == 0 && previous_review_interval.is_some() {
                let (min, max) =
                    ctx.min_and_max_review_intervals(ctx.minimum_lapse_interval.max(floor));
                (interval.round() as u32).clamp(min, max)
            } else {
                let floor = previous_review_interval.map_or(floor, |previous| {
                    minimum_review_fuzz_interval(interval, previous, ctx.maximum_review_interval)
                        .max(floor)
                });
                let (min, max) = ctx.min_and_max_review_intervals(floor);
                let interval = if previous_review_interval.is_none() {
                    interval.round().max(1.0)
                } else {
                    interval
                };
                ctx.with_review_fuzz(interval, min, max)
            };
            previous_days = days;
            Some(ButtonInterval::Days(days))
        }
    })
}

impl ButtonInterval {
    pub(super) fn learning(
        self,
        remaining_steps: u32,
        memory_state: Option<crate::card::FsrsMemoryState>,
        ctx: &StateContext,
    ) -> CardState {
        match self {
            Self::Secs(scheduled_secs) => LearnState {
                remaining_steps,
                scheduled_secs,
                elapsed_secs: 0,
                memory_state,
            }
            .into(),
            Self::Days(scheduled_days) => ReviewState {
                scheduled_days,
                ease_factor: ctx.initial_ease_factor,
                memory_state,
                ..Default::default()
            }
            .into(),
        }
    }

    pub(super) fn reviewing(self, mut review: ReviewState, remaining_steps: u32) -> CardState {
        review.elapsed_days = 0;
        match self {
            Self::Secs(scheduled_secs) => {
                review.scheduled_days = 1;
                RelearnState {
                    learning: LearnState {
                        remaining_steps,
                        scheduled_secs,
                        elapsed_secs: 0,
                        memory_state: review.memory_state,
                    },
                    review,
                }
                .into()
            }
            Self::Days(days) => {
                review.scheduled_days = days;
                review.into()
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn model_intervals_follow_configured_steps_and_passing_reviews_do_not_lapse() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.fsrs7 = true;
        let mut model = fsrs::FSRS::new(&fsrs::DEFAULT_PARAMETERS)
            .unwrap()
            .next_states(None, 0.9, 0)
            .unwrap();
        for (state, interval) in [
            &mut model.again,
            &mut model.hard,
            &mut model.good,
            &mut model.easy,
        ]
        .into_iter()
        .zip([0.001, 0.002, 0.003, 0.004])
        {
            state.interval = interval;
        }
        ctx.fsrs_next_states = Some(model);
        let learning = LearnState {
            remaining_steps: 1,
            scheduled_secs: 600,
            elapsed_secs: 600,
            memory_state: None,
        };
        let states = learning.next_states(&ctx);
        // Again/Hard retain configured steps; Good/Easy use fractional output
        // after the last configured step instead of forcing one-day graduation.
        assert_eq!(states.again.interval_kind(), IntervalKind::InSecs(60));
        assert_eq!(states.hard.interval_kind(), IntervalKind::InSecs(600));
        assert_eq!(states.good.interval_kind(), IntervalKind::InSecs(259));
        assert_eq!(states.easy.interval_kind(), IntervalKind::InSecs(346));
        ctx.steps = LearningSteps::new(&[]);
        ctx.relearn_steps = LearningSteps::new(&[]);
        let review = ReviewState {
            scheduled_days: 10,
            lapses: 2,
            ..Default::default()
        };
        let states = review.next_states(&ctx);
        for (index, state) in [states.again, states.hard, states.good, states.easy]
            .into_iter()
            .enumerate()
        {
            assert!(matches!(
                state,
                CardState::Normal(NormalState::Relearning(_))
            ));
            assert_eq!(
                state.review_state().unwrap().lapses,
                if index == 0 { 3 } else { 2 }
            );
        }
        let relearning = RelearnState { learning, review };
        for states in [learning.next_states(&ctx), relearning.next_states(&ctx)] {
            for (state, secs) in [states.again, states.hard, states.good, states.easy]
                .into_iter()
                .zip([86, 173, 259, 346])
            {
                assert_eq!(state.interval_kind(), IntervalKind::InSecs(secs));
            }
        }
    }

    #[test]
    fn fractional_buttons_remain_ordered_and_days_respect_maximum() {
        let mut ctx = StateContext::defaults_for_testing();
        assert_eq!(
            button_intervals(&ctx, [Some(0.01), Some(0.005), Some(0.5), Some(0.75)], None),
            [
                Some(ButtonInterval::Secs(864)),
                Some(ButtonInterval::Secs(864)),
                Some(ButtonInterval::Days(1)),
                Some(ButtonInterval::Days(2))
            ]
        );
        ctx.maximum_review_interval = 2;
        assert_eq!(
            button_intervals(&ctx, [Some(3.0); 4], Some(1)),
            [Some(ButtonInterval::Days(2)); 4]
        );
        assert_eq!(
            button_intervals(&ctx, [None, None, Some(2.0), Some(2.0)], None),
            [
                None,
                None,
                Some(ButtonInterval::Days(2)),
                Some(ButtonInterval::Days(2))
            ]
        );
    }
}
