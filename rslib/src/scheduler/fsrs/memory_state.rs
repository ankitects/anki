// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRSItem;
use fsrs::MemoryState;
use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;
use itertools::Either;
use itertools::Itertools;

use super::params::ignore_revlogs_before_ms_from_config;
use super::rescheduler::Rescheduler;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::answering::get_fuzz_seed;
use crate::scheduler::fsrs::params::reviews_for_fsrs;
use crate::scheduler::fsrs::params::Params;
use crate::scheduler::states::fuzz::minimum_review_fuzz_interval;
use crate::scheduler::states::fuzz::with_review_fuzz;
use crate::search::Negated;
use crate::search::Node;
use crate::search::SearchNode;
use crate::search::StateKind;

#[derive(Debug, Clone, Copy, Default)]
pub struct ComputeMemoryProgress {
    pub current_cards: u32,
    pub total_cards: u32,
}

/// Helper function to determine the appropriate decay value based on FSRS
/// parameters
pub(crate) fn get_decay_from_params(params: &[f32]) -> f32 {
    if params.is_empty() {
        fsrs::DEFAULT_PARAMETERS[23]
    } else if params.len() < 21 {
        FSRS5_DEFAULT_DECAY // default decay for FSRS-4.5 and FSRS-5
    } else if params.len() >= 34 {
        // FSRS-7 uses a mixture curve. This value is retained only for old
        // compatibility consumers; built-in metrics use the complete state.
        params[23]
    } else {
        params[20]
    }
}

pub(crate) fn fsrs_memory_state_for_fsrs(
    fsrs: &FSRS,
    memory_state: MemoryState,
) -> FsrsMemoryState {
    FsrsMemoryState {
        stability: fsrs.interval_at_retrievability(memory_state, 0.9),
        stability_internal: memory_state.stability,
        stability_fast: Some(memory_state.stability_fast),
        difficulty: memory_state.difficulty,
    }
}

pub(crate) fn fsrs_current_retrievability_for_state(
    params: &[f32],
    state: FsrsMemoryState,
    elapsed_days: f32,
) -> Result<f32> {
    let retrievability =
        FSRS::new(params)?.current_retrievability(state.into(), elapsed_days.max(0.0));
    require!(retrievability.is_finite(), "invalid FSRS parameter values");
    Ok(retrievability)
}

#[derive(Debug, Clone)]
