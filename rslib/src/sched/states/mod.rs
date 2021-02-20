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

pub use {
    filtered::FilteredState, learning::LearnState, new::NewState, normal::NormalState,
    preview_filter::PreviewState, relearning::RelearnState,
    rescheduling_filter::ReschedulingFilterState, review::ReviewState,
};

pub(crate) use interval_kind::IntervalKind;

use crate::revlog::RevlogReviewKind;

use self::steps::LearningSteps;

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
            CardState::Normal(state) => state.next_states(&ctx),
            CardState::Filtered(state) => state.next_states(&ctx),
        }
    }
}

/// Info required during state transitions.
pub(crate) struct StateContext<'a> {
    pub fuzz_seed: Option<u64>,

    // learning
    pub steps: LearningSteps<'a>,
    pub graduating_interval_good: u32,
    pub graduating_interval_easy: u32,

    // reviewing
    pub hard_multiplier: f32,
    pub easy_multiplier: f32,
    pub interval_multiplier: f32,
    pub maximum_review_interval: u32,

    // relearning
    pub relearn_steps: LearningSteps<'a>,
    pub lapse_multiplier: f32,
    pub minimum_lapse_interval: u32,

    // filtered
    pub in_filtered_deck: bool,
}

#[derive(Debug)]
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
