// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod filtered;
pub(crate) mod interval_kind;
pub(crate) mod learning;
pub(crate) mod new;
pub(crate) mod normal;
pub(crate) mod preview_filter;
pub(crate) mod relearning;
pub(crate) mod rescheduling_filter;
pub(crate) mod review;
pub(crate) mod steps;

pub use filtered::FilteredState;
pub(crate) use interval_kind::IntervalKind;
pub use learning::LearnState;
pub use new::NewState;
pub use normal::NormalState;
pub use preview_filter::PreviewState;
pub use relearning::RelearnState;
pub use rescheduling_filter::ReschedulingFilterState;
pub use review::ReviewState;

use self::steps::LearningSteps;
use crate::revlog::RevlogReviewKind;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum CardState {
    Normal(NormalState),
    Filtered(FilteredState),
}

impl CardState {
    pub(crate) fn interval_kind(self) -> IntervalKind {
        match self {
            CardState::Normal(normal) => normal.interval_kind(),
            CardState::Filtered(filtered) => filtered.interval_kind(),
        }
    }

    pub(crate) fn revlog_kind(self) -> RevlogReviewKind {
        match self {
            CardState::Normal(normal) => normal.revlog_kind(),
            CardState::Filtered(filtered) => filtered.revlog_kind(),
        }
    }

    pub(crate) fn next_states(self, ctx: &StateContext) -> NextCardStates {
        match self {
            CardState::Normal(state) => state.next_states(ctx),
            CardState::Filtered(state) => state.next_states(ctx),
        }
    }

    /// Returns underlying review state, if it exists.
    pub(crate) fn review_state(self) -> Option<ReviewState> {
        match self {
            CardState::Normal(state) => state.review_state(),
            CardState::Filtered(state) => state.review_state(),
        }
    }

    pub(crate) fn leeched(self) -> bool {
        self.review_state().map(|r| r.leeched).unwrap_or_default()
    }
}

/// Info required during state transitions.
pub(crate) struct StateContext<'a> {
    /// In range `0.0..1.0`. Used to pick the final interval from the fuzz range.
    pub fuzz_factor: Option<f32>,

    // learning
    pub steps: LearningSteps<'a>,
    pub graduating_interval_good: u32,
    pub graduating_interval_easy: u32,
    pub initial_ease_factor: f32,

    // reviewing
    pub hard_multiplier: f32,
    pub easy_multiplier: f32,
    pub interval_multiplier: f32,
    pub maximum_review_interval: u32,
    pub leech_threshold: u32,

    // relearning
    pub relearn_steps: LearningSteps<'a>,
    pub lapse_multiplier: f32,
    pub minimum_lapse_interval: u32,

    // filtered
    pub in_filtered_deck: bool,
    pub preview_step: u32,
}

impl<'a> StateContext<'a> {
    /// Return the minimum and maximum review intervals.
    /// - `maximum` is `self.maximum_review_interval`, but at least 1.
    /// - `minimum` is as passed, but at least 1, and at most `maximum`.
    pub(crate) fn min_and_max_review_intervals(&self, minimum: u32) -> (u32, u32) {
        let maximum = self.maximum_review_interval.max(1);
        let minimum = minimum.max(1).min(maximum);
        (minimum, maximum)
    }

    /// Apply fuzz, respecting the passed bounds.
    /// Caller must ensure reasonable bounds.
    pub(crate) fn with_review_fuzz(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        if let Some(fuzz_factor) = self.fuzz_factor {
            let (lower, upper) = constrained_fuzz_bounds(interval, minimum, maximum);
            (lower as f32 + fuzz_factor * ((1 + upper - lower) as f32)).floor() as u32
        } else {
            (interval.round() as u32).max(minimum).min(maximum)
        }
    }

    pub(crate) fn fuzzed_graduating_interval_good(&self) -> u32 {
        let (minimum, maximum) = self.min_and_max_review_intervals(1);
        self.with_review_fuzz(self.graduating_interval_good as f32, minimum, maximum)
    }

    pub(crate) fn fuzzed_graduating_interval_easy(&self) -> u32 {
        let (minimum, maximum) = self.min_and_max_review_intervals(1);
        self.with_review_fuzz(self.graduating_interval_easy as f32, minimum, maximum)
    }

    #[cfg(test)]
    pub(crate) fn defaults_for_testing() -> Self {
        Self {
            fuzz_factor: None,
            steps: LearningSteps::new(&[60.0, 600.0]),
            graduating_interval_good: 1,
            graduating_interval_easy: 4,
            initial_ease_factor: 2.5,
            hard_multiplier: 1.2,
            easy_multiplier: 1.3,
            interval_multiplier: 1.0,
            maximum_review_interval: 36500,
            leech_threshold: 8,
            relearn_steps: LearningSteps::new(&[600.0]),
            lapse_multiplier: 0.0,
            minimum_lapse_interval: 1,
            in_filtered_deck: false,
            preview_step: 10,
        }
    }
}

/// Return the bounds of the fuzz range, respecting `minimum` and `maximum`.
/// Ensure the upper bound is larger than the lower bound, if `maximum` allows
/// it and it is larger than 1.
fn constrained_fuzz_bounds(interval: f32, minimum: u32, maximum: u32) -> (u32, u32) {
    let (lower, mut upper) = fuzz_bounds(interval);
    let lower = lower.max(minimum);
    if upper == lower && upper != 1 {
        upper = lower + 1;
    };
    (lower, upper.min(maximum))
}

fn fuzz_bounds(interval: f32) -> (u32, u32) {
    if interval < 2.0 {
        (1, 1)
    } else if interval < 3.0 {
        (2, 3)
    } else if interval < 7.0 {
        fuzz_range(interval, 0.25, 0.0)
    } else if interval < 30.0 {
        fuzz_range(interval, 0.15, 2.0)
    } else {
        fuzz_range(interval, 0.05, 4.0)
    }
}

fn fuzz_range(interval: f32, factor: f32, minimum: f32) -> (u32, u32) {
    let delta = (interval * factor).max(minimum).max(1.0);
    (
        (interval - delta).round() as u32,
        (interval + delta).round() as u32,
    )
}

#[derive(Debug, Clone)]
pub struct NextCardStates {
    pub current: CardState,
    pub again: CardState,
    pub hard: CardState,
    pub good: CardState,
    pub easy: CardState,
}

impl From<NewState> for CardState {
    fn from(state: NewState) -> Self {
        CardState::Normal(state.into())
    }
}

impl From<ReviewState> for CardState {
    fn from(state: ReviewState) -> Self {
        CardState::Normal(state.into())
    }
}

impl From<LearnState> for CardState {
    fn from(state: LearnState) -> Self {
        CardState::Normal(state.into())
    }
}

impl From<RelearnState> for CardState {
    fn from(state: RelearnState) -> Self {
        CardState::Normal(state.into())
    }
}

impl From<NormalState> for CardState {
    fn from(state: NormalState) -> Self {
        CardState::Normal(state)
    }
}

impl From<PreviewState> for CardState {
    fn from(state: PreviewState) -> Self {
        CardState::Filtered(FilteredState::Preview(state))
    }
}

impl From<ReschedulingFilterState> for CardState {
    fn from(state: ReschedulingFilterState) -> Self {
        CardState::Filtered(FilteredState::Rescheduling(state))
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn min_and_max_review_intervals() {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.maximum_review_interval = 0;
        assert_eq!(ctx.min_and_max_review_intervals(0), (1, 1));
        assert_eq!(ctx.min_and_max_review_intervals(2), (1, 1));
        ctx.maximum_review_interval = 3;
        assert_eq!(ctx.min_and_max_review_intervals(0), (1, 3));
        assert_eq!(ctx.min_and_max_review_intervals(2), (2, 3));
        assert_eq!(ctx.min_and_max_review_intervals(4), (3, 3));
    }

    fn assert_lower_middle_upper(
        ctx: &mut StateContext,
        interval: f32,
        minimum: u32,
        maximum: u32,
        lower: u32,
        middle: u32,
        upper: u32,
    ) {
        ctx.fuzz_factor = Some(0.0);
        assert_eq!(ctx.with_review_fuzz(interval, minimum, maximum), lower);
        ctx.fuzz_factor = Some(0.5);
        assert_eq!(ctx.with_review_fuzz(interval, minimum, maximum), middle);
        ctx.fuzz_factor = Some(0.99);
        assert_eq!(ctx.with_review_fuzz(interval, minimum, maximum), upper);
    }

    #[test]
    fn with_review_fuzz() {
        let mut ctx = StateContext::defaults_for_testing();

        // no fuzz
        assert_eq!(ctx.with_review_fuzz(1.5, 1, 100), 2);
        assert_eq!(ctx.with_review_fuzz(0.1, 1, 100), 1);
        assert_eq!(ctx.with_review_fuzz(101.0, 1, 100), 100);

        // no fuzzing for an interval of 1
        assert_lower_middle_upper(&mut ctx, 1.0, 1, 1000, 1, 1, 1);
        // fuzz range is (2, 3) for an interval of 2
        assert_lower_middle_upper(&mut ctx, 2.0, 1, 1000, 2, 3, 3);
        // 25%, 15%, 5% percent fuzz, but at least 1, 2, 4
        assert_lower_middle_upper(&mut ctx, 5.0, 1, 1000, 4, 5, 6);
        assert_lower_middle_upper(&mut ctx, 20.0, 1, 1000, 17, 20, 23);
        assert_lower_middle_upper(&mut ctx, 100.0, 1, 1000, 95, 100, 105);

        // ensure fuzz range of at least 2, if allowed
        assert_lower_middle_upper(&mut ctx, 2.0, 2, 1000, 2, 3, 3);
        assert_lower_middle_upper(&mut ctx, 2.0, 3, 1000, 3, 4, 4);
        assert_lower_middle_upper(&mut ctx, 2.0, 3, 3, 3, 3, 3);

        // respect limits and preserve uniform distribution of valid intervals
        assert_lower_middle_upper(&mut ctx, 100.0, 101, 1000, 101, 103, 105);
        assert_lower_middle_upper(&mut ctx, 100.0, 1, 99, 95, 97, 99);
        assert_lower_middle_upper(&mut ctx, 100.0, 97, 103, 97, 100, 103);
    }
}
