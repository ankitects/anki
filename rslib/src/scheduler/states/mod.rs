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
use rand::{prelude::*, rngs::StdRng};
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

    pub(crate) fn revlog_kind(self) -> Option<RevlogReviewKind> {
        match self {
            CardState::Normal(normal) => Some(normal.revlog_kind()),
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
    pub fuzz_seed: Option<u64>,

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
    pub(crate) fn with_review_fuzz(&self, interval: f32) -> u32 {
        // fixme: floor() is to match python
        let interval = interval.floor();
        if let Some(seed) = self.fuzz_seed {
            let mut rng = StdRng::seed_from_u64(seed);
            let (lower, upper) = if interval < 2.0 {
                (1.0, 1.0)
            } else if interval < 3.0 {
                (2.0, 3.0)
            } else if interval < 7.0 {
                fuzz_range(interval, 0.25, 0.0)
            } else if interval < 30.0 {
                fuzz_range(interval, 0.15, 2.0)
            } else {
                fuzz_range(interval, 0.05, 4.0)
            };
            if lower >= upper {
                lower
            } else {
                rng.gen_range(lower..upper)
            }
        } else {
            interval
        }
        .round() as u32
    }

    pub(crate) fn fuzzed_graduating_interval_good(&self) -> u32 {
        self.with_review_fuzz(self.graduating_interval_good as f32)
    }

    pub(crate) fn fuzzed_graduating_interval_easy(&self) -> u32 {
        self.with_review_fuzz(self.graduating_interval_easy as f32)
    }
}

fn fuzz_range(interval: f32, factor: f32, minimum: f32) -> (f32, f32) {
    let delta = (interval * factor).max(minimum).max(1.0);
    (interval - delta, interval + delta + 1.0)
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
