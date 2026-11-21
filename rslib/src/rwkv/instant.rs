// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! A thin wrapper around the RWKV engine that serves RWKV-Instant recall
//! predictions with the bundled SM98 model.

use std::io;

use super::ReviewInput;
use super::RwkvInference;
use super::StatePrecision;

/// The show-time instant-only small model (SM98) from the JSchoreels/anki
/// fork: trained only to predict the next answer at the moment a card is
/// shown. It has no trained forgetting curve.
static SM98_WEIGHTS: &[u8] = include_bytes!("sm98.weights.bin");

/// Curve outputs are unused by the instant-only model, but the engine still
/// needs a target retention and maximum interval.
const UNUSED_TARGET_RETENTION: f32 = 0.9;
const UNUSED_MAX_INTERVAL_DAYS: u32 = 36_500;

pub(crate) struct InstantModel {
    engine: RwkvInference,
}

impl InstantModel {
    pub(crate) fn load() -> io::Result<Self> {
        let engine = RwkvInference::load_from_bytes(
            SM98_WEIGHTS,
            UNUSED_TARGET_RETENTION,
            UNUSED_MAX_INTERVAL_DAYS,
            StatePrecision::F32,
        )?
        // SM98 was trained without the deleted-card signal.
        .with_leak_free_inputs()?;
        Ok(Self { engine })
    }

    /// Feed answered reviews (oldest first) into the recurrent state.
    pub(crate) fn replay(&mut self, reviews: Vec<ReviewInput>) -> io::Result<()> {
        if reviews.is_empty() {
            return Ok(());
        }
        self.engine.warm_up_reviews(reviews, false).map(|_| ())
    }

    /// Probability of recall if each card were shown now. Queries do not
    /// change the state.
    pub(crate) fn predict(&mut self, queries: Vec<ReviewInput>) -> io::Result<Vec<f32>> {
        if queries.is_empty() {
            return Ok(vec![]);
        }
        self.engine
            .predict_retrievability_many_from_warm_up(queries)
    }
}
