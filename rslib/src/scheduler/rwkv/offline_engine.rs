// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! The offline runtime's RWKV: the fork's 2.76M-parameter model
//! (`crate::rwkv`) or JSchoreels' 331k-parameter one (`crate::rwkv::alternate`,
//! the fork's second compile of the engine), picked by the model file the app
//! passes (kuma3's Settings > RWKV model). Both engines take the same inputs;
//! the small one gets copies of its own types.
//!
//! Since the fork's build 115 a model size (Big, Small, Tiny) is a two-head
//! model (curve, intervals, S90) and an Instant model; with presets using
//! both RWKV-Instant and RWKV-Curve both run as a [Engine::Pair] replaying
//! the same history (the desktop's paired runtime, `crate::rwkv::runtime`).

use std::fs;
use std::io;
use std::path::PathBuf;

use crate::rwkv;
use crate::rwkv::alternate as rwkv_small;
use crate::rwkv::ReviewInput;
use crate::rwkv::StatePrecision;

pub(super) enum Engine {
    Big(rwkv::RwkvInference),
    Small(rwkv_small::RwkvInference),
    /// The two-head model for RWKV-Curve and the Instant model for
    /// RWKV-Instant's recall, each with its own state.
    Pair {
        curve: Box<Engine>,
        instant: Box<Engine>,
    },
}

/// See [rwkv::RwkvInference::state_snapshot].
pub(super) enum Snapshot {
    Big(rwkv::RwkvStateSnapshot),
    Small(rwkv_small::RwkvStateSnapshot),
    /// The curve model's, the instant model's, and their joined cache state.
    Pair(Box<Snapshot>, Box<Snapshot>, Vec<u8>),
}

/// The small model's weights carry its 3-power-curve head (`gru_w_weight`);
/// the big model's file has no such tensor.
fn is_small_model(weights: &[u8]) -> bool {
    weights.windows(12).any(|name| name == b"gru_w_weight")
}

fn small_precision(precision: StatePrecision) -> rwkv_small::StatePrecision {
    match precision {
        StatePrecision::F32 => rwkv_small::StatePrecision::F32,
        StatePrecision::Int8 => rwkv_small::StatePrecision::Int8,
        StatePrecision::Int4 => rwkv_small::StatePrecision::Int4,
        StatePrecision::Int4CardsInt8Notes => rwkv_small::StatePrecision::Int4CardsInt8Notes,
    }
}

/// The four answers' intervals in days and their S90s.
type CurvePair = ([Option<f32>; 4], [Option<f32>; 4]);

impl Engine {
    pub(super) fn load_with_precision(
        path: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        leak_free_inputs: bool,
    ) -> io::Result<Self> {
        Ok(if is_small_model(&fs::read(&path)?) {
            let engine = rwkv_small::RwkvInference::load_with_precision(
                path,
                target_retention,
                max_interval_days,
                small_precision(precision),
            )?;
            let engine = if leak_free_inputs {
                engine.with_leak_free_inputs()?
            } else {
                engine
            };
            Self::Small(engine)
        } else {
            // the fork's build 115 big models (SM91s2, SM99) are leak-free too
            let engine = rwkv::RwkvInference::load_with_precision(
                path,
                target_retention,
                max_interval_days,
                precision,
            )?;
            Self::Big(if leak_free_inputs {
                engine.with_leak_free_inputs()?
            } else {
                engine
            })
        })
    }

    /// A size's two models side by side: both must load with the same
    /// precision and input rule.
    pub(super) fn load_pair(
        curve: PathBuf,
        instant: PathBuf,
        target_retention: f32,
        max_interval_days: u32,
        precision: StatePrecision,
        leak_free_inputs: bool,
    ) -> io::Result<Self> {
        let load = |path| {
            Self::load_with_precision(
                path,
                target_retention,
                max_interval_days,
                precision,
                leak_free_inputs,
            )
            .map(Box::new)
        };
        Ok(Self::Pair {
            curve: load(curve)?,
            instant: load(instant)?,
        })
    }

    pub(super) fn cache_state(&self) -> Vec<u8> {
        match self {
            Self::Big(engine) => engine.cache_state(),
            Self::Small(engine) => engine.cache_state(),
            Self::Pair { curve, instant } => {
                rwkv::paired::join_cache(&curve.cache_state(), &instant.cache_state())
            }
        }
    }

    pub(super) fn restore_cache_state(&mut self, bytes: &[u8]) -> io::Result<()> {
        match self {
            Self::Big(engine) => engine.restore_cache_state(bytes),
            Self::Small(engine) => engine.restore_cache_state(bytes),
            Self::Pair { curve, instant } => {
                let (curve_bytes, instant_bytes) = rwkv::paired::split_cache(bytes)?;
                curve.restore_cache_state(curve_bytes)?;
                instant.restore_cache_state(instant_bytes)
            }
        }
    }

    pub(super) fn reset_warm_up_state(&mut self) {
        match self {
            Self::Big(engine) => engine.reset_warm_up_state(),
            Self::Small(engine) => engine.reset_warm_up_state(),
            Self::Pair { curve, instant } => {
                curve.reset_warm_up_state();
                instant.reset_warm_up_state();
            }
        }
    }

    /// A pair's states are stored one after the other, the curve model's
    /// first (each is self-delimiting).
    pub(super) fn read_warm_up_states(&mut self, input: &mut impl io::Read) -> io::Result<()> {
        match self {
            Self::Big(engine) => engine.read_warm_up_states(input),
            Self::Small(engine) => engine.read_warm_up_states(input),
            Self::Pair { curve, instant } => {
                curve.read_warm_up_states(input)?;
                instant.read_warm_up_states(input)
            }
        }
    }

    pub(super) fn state_snapshot(&self) -> Snapshot {
        match self {
            Self::Big(engine) => Snapshot::Big(engine.state_snapshot()),
            Self::Small(engine) => Snapshot::Small(engine.state_snapshot()),
            Self::Pair { curve, instant } => {
                let (curve, instant) = (curve.state_snapshot(), instant.state_snapshot());
                let cache = rwkv::paired::join_cache(curve.cache_state(), instant.cache_state());
                Snapshot::Pair(Box::new(curve), Box::new(instant), cache)
            }
        }
    }

    pub(super) fn warm_up_reviews(
        &mut self,
        reviews: Vec<ReviewInput>,
        record_predictions: bool,
    ) -> io::Result<Vec<(usize, f32)>> {
        match self {
            Self::Big(engine) => engine.warm_up_reviews(reviews, record_predictions),
            Self::Small(engine) => engine.warm_up_reviews(reviews, record_predictions),
            // both models see every answer, in parallel; the recorded
            // predictions are RWKV-Instant's recall
            Self::Pair { curve, instant } => {
                let curve_reviews = reviews.clone();
                let (curve_result, instant_result) = rayon::join(
                    || curve.warm_up_reviews(curve_reviews, false),
                    || instant.warm_up_reviews(reviews, record_predictions),
                );
                curve_result?;
                instant_result
            }
        }
    }

    /// RWKV-Curve for one card from its warm-up state: the four answers'
    /// unrounded intervals in days at the input's target retentions, and
    /// their S90s (the desktop's `predict_many`: a query pass plus one per
    /// simulated answer).
    pub(super) fn curve_from_warm_up(&mut self, input: ReviewInput) -> io::Result<CurvePair> {
        Ok(match self {
            Self::Big(engine) => {
                let state = engine.warm_up_state(&input);
                let out =
                    engine.predict_many(vec![rwkv::ReviewPredictionRequest { input, state }])?;
                out.first().map(|o| (o.intervals, o.s90s))
            }
            Self::Small(engine) => {
                let state = engine.warm_up_state(&input);
                let out = engine
                    .predict_many(vec![rwkv_small::ReviewPredictionRequest { input, state }])?;
                out.first().map(|o| (o.intervals, o.s90s))
            }
            Self::Pair { curve, .. } => return curve.curve_from_warm_up(input),
        }
        .unwrap_or_default())
    }

    /// RWKV-Curve's current curve of each card from its warm-up state: the
    /// whole-day interval where it meets the input's Good target, and its
    /// S90 (what "Reschedule Cards with RWKV-Curve Intervals" applies).
    pub(super) fn curve_current_many(
        &mut self,
        inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<(Option<u32>, Option<f32>)>> {
        // ponytail: predict_many runs the 4 answer passes too; a query-only
        // variant if a big deck is slow to reschedule
        Ok(match self {
            Self::Big(engine) => {
                let requests = inputs
                    .into_iter()
                    .map(|input| {
                        let state = engine.warm_up_state(&input);
                        rwkv::ReviewPredictionRequest { input, state }
                    })
                    .collect();
                engine
                    .predict_many(requests)?
                    .into_iter()
                    .map(|o| (o.current_interval, o.current_s90))
                    .collect()
            }
            Self::Small(engine) => {
                let requests = inputs
                    .into_iter()
                    .map(|input| {
                        let state = engine.warm_up_state(&input);
                        rwkv_small::ReviewPredictionRequest { input, state }
                    })
                    .collect();
                engine
                    .predict_many(requests)?
                    .into_iter()
                    .map(|o| (o.current_interval, o.current_s90))
                    .collect()
            }
            Self::Pair { curve, .. } => curve.curve_current_many(inputs)?,
        })
    }

    pub(super) fn predict_retrievability_many_from_warm_up(
        &mut self,
        inputs: Vec<ReviewInput>,
    ) -> io::Result<Vec<f32>> {
        match self {
            Self::Big(engine) => engine.predict_retrievability_many_from_warm_up(inputs),
            Self::Small(engine) => engine.predict_retrievability_many_from_warm_up(inputs),
            Self::Pair { instant, .. } => instant.predict_retrievability_many_from_warm_up(inputs),
        }
    }

    /// Workload simulations schedule with the curve: a pair runs them on its
    /// two-head model, as the desktop's paired runtime. The state is left
    /// unchanged.
    pub(super) fn simulate_workload_from_warm_up(
        &self,
        inputs: Vec<rwkv::RwkvWorkloadSimulationInput>,
        config: rwkv::RwkvWorkloadSimulationConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<rwkv::RwkvWorkloadSimulationOutput> {
        match self {
            Self::Big(engine) => engine.simulate_workload_from_warm_up(inputs, config, progress),
            Self::Small(engine) => engine.simulate_workload_from_warm_up(inputs, config, progress),
            Self::Pair { curve, .. } => {
                curve.simulate_workload_from_warm_up(inputs, config, progress)
            }
        }
    }

    pub(super) fn compare_workloads_from_warm_up(
        &self,
        inputs: Vec<rwkv::RwkvComparisonCard>,
        config: rwkv::RwkvComparisonConfig,
        progress: &mut dyn FnMut(u32, u32) -> io::Result<()>,
    ) -> io::Result<rwkv::RwkvComparisonOutput> {
        match self {
            Self::Big(engine) => engine.compare_workloads_from_warm_up(inputs, config, progress),
            Self::Small(engine) => engine.compare_workloads_from_warm_up(inputs, config, progress),
            Self::Pair { curve, .. } => {
                curve.compare_workloads_from_warm_up(inputs, config, progress)
            }
        }
    }
}

impl Snapshot {
    pub(super) fn write_warm_up_states(&self, out: &mut impl io::Write) -> io::Result<()> {
        match self {
            Self::Big(state) => state.write_warm_up_states(out),
            Self::Small(state) => state.write_warm_up_states(out),
            Self::Pair(curve, instant, _) => {
                curve.write_warm_up_states(out)?;
                instant.write_warm_up_states(out)
            }
        }
    }

    pub(super) fn cache_state(&self) -> &[u8] {
        match self {
            Self::Big(state) => state.cache_state(),
            Self::Small(state) => state.cache_state(),
            Self::Pair(_, _, cache) => cache,
        }
    }
}

#[cfg(test)]
mod tests {
    use anki_proto::scheduler::RwkvPrepareOfflineRequest;

    use super::*;
    use crate::collection::Collection;

    #[test]
    fn unsupported_offline_model_kind_is_rejected() {
        let mut col = Collection::new();
        let result = col.rwkv_prepare_offline(RwkvPrepareOfflineRequest {
            model_path: "not-opened".into(),
            model_kind: "unknown".into(),
            ..Default::default()
        });
        assert!(result.is_err());
        assert!(col.state.rwkv_offline.is_none());
    }

    fn model_file(name: &str) -> PathBuf {
        PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../qt/aqt/rwkv_inference")
            .join(name)
    }

    #[test]
    fn sm72_offline_engine_enables_the_leak_free_rule() {
        let weights = model_file("sm72/weights.f32.bin");
        let predict = |precision, leak_free| {
            let mut engine =
                Engine::load_with_precision(weights.clone(), 0.9, 36_500, precision, leak_free)
                    .unwrap();
            let mut reviews: Vec<_> = (0..14)
                .map(|index| ReviewInput {
                    card_id: 100 + index % 7,
                    note_id: None,
                    deck_id: None,
                    preset_id: None,
                    is_query: false,
                    ease: Some((index % 4 + 1) as u8),
                    duration_millis: Some(1234 + index * 7),
                    card_type: Some(if index < 7 { 0 } else { 2 }),
                    day_offset: Some(1000 + index / 7),
                    current_elapsed_days: Some(index / 7),
                    current_elapsed_seconds: Some(index / 7 * 86_400),
                    target_retentions: [Some(0.9); 4],
                    enforce_grade_order: true,
                })
                .collect();
            let mut query = reviews[0].clone();
            query.is_query = true;
            query.ease = None;
            query.duration_millis = None;
            engine
                .warm_up_reviews(std::mem::take(&mut reviews), false)
                .unwrap();
            engine
                .predict_retrievability_many_from_warm_up(vec![query])
                .unwrap()[0]
        };
        for precision in [
            StatePrecision::F32,
            StatePrecision::Int8,
            StatePrecision::Int4CardsInt8Notes,
        ] {
            assert_ne!(predict(precision, true), predict(precision, false));
        }
        assert!(Engine::load_with_precision(
            model_file("RWKV_trained_on_5000_10000.bin"),
            0.9,
            36_500,
            StatePrecision::F32,
            true,
        )
        .is_ok());
    }

    fn history() -> (Vec<ReviewInput>, ReviewInput) {
        let reviews: Vec<_> = (0..21)
            .map(|index| ReviewInput {
                card_id: 100 + index % 7,
                note_id: Some(50 + index % 3),
                deck_id: Some(1),
                preset_id: Some(1),
                is_query: false,
                ease: Some((index % 4 + 1) as u8),
                duration_millis: Some(1234 + index * 7),
                card_type: Some(if index < 7 { 0 } else { 2 }),
                day_offset: Some(1000 + index / 7),
                current_elapsed_days: Some(index / 7),
                current_elapsed_seconds: Some(index / 7 * 86_400),
                target_retentions: [Some(0.9); 4],
                enforce_grade_order: true,
            })
            .collect();
        let mut query = reviews[3].clone();
        query.is_query = true;
        query.ease = None;
        query.duration_millis = None;
        query.day_offset = Some(1005);
        query.current_elapsed_days = Some(3);
        query.current_elapsed_seconds = Some(3 * 86_400);
        (reviews, query)
    }

    /// Each size's pair answers RWKV-Instant's recall as its Instant model
    /// alone and RWKV-Curve as its two-head model alone, and its saved
    /// state loads back to the same answers.
    #[test]
    fn a_pair_is_its_two_models_side_by_side() {
        let load = |file: &str| {
            Engine::load_with_precision(model_file(file), 0.9, 36_500, StatePrecision::Int8, true)
                .unwrap()
        };
        for (two_head, instant_model) in [
            ("sm92/weights.f32.bin", "sm98/weights.f32.bin"),
            ("sm106/weights.f32.bin", "sm101/weights.f32.bin"),
            ("RWKV_trained_on_5000_10000.bin", "sm99/weights.f32.bin"),
        ] {
            let (reviews, query) = history();
            let mut pair = Engine::load_pair(
                model_file(two_head),
                model_file(instant_model),
                0.9,
                36_500,
                StatePrecision::Int8,
                true,
            )
            .unwrap();
            let (mut curve, mut instant) = (load(two_head), load(instant_model));
            let recorded = pair.warm_up_reviews(reviews.clone(), true).unwrap();
            curve.warm_up_reviews(reviews.clone(), false).unwrap();
            assert_eq!(recorded, instant.warm_up_reviews(reviews, true).unwrap());

            let recall = |engine: &mut Engine| {
                engine
                    .predict_retrievability_many_from_warm_up(vec![query.clone()])
                    .unwrap()
            };
            let intervals = |engine: &mut Engine| engine.curve_from_warm_up(query.clone()).unwrap();
            assert_eq!(recall(&mut pair), recall(&mut instant), "{two_head}");
            assert_ne!(recall(&mut pair), recall(&mut curve), "{two_head}");
            assert_eq!(intervals(&mut pair), intervals(&mut curve), "{two_head}");

            // the state file's part: both states and the joined cache
            let snapshot = pair.state_snapshot();
            let mut bytes = Vec::new();
            snapshot.write_warm_up_states(&mut bytes).unwrap();
            let mut restored = Engine::load_pair(
                model_file(two_head),
                model_file(instant_model),
                0.9,
                36_500,
                StatePrecision::Int8,
                true,
            )
            .unwrap();
            let mut input = bytes.as_slice();
            restored.read_warm_up_states(&mut input).unwrap();
            assert!(input.is_empty());
            restored
                .restore_cache_state(snapshot.cache_state())
                .unwrap();
            // the file keeps some states in half floats: compare with each model
            // saved and restored alone
            let round_trip = |engine: &Engine, file: &str| {
                let snapshot = engine.state_snapshot();
                let mut bytes = Vec::new();
                snapshot.write_warm_up_states(&mut bytes).unwrap();
                let mut restored = load(file);
                restored.read_warm_up_states(&mut bytes.as_slice()).unwrap();
                restored
                    .restore_cache_state(snapshot.cache_state())
                    .unwrap();
                restored
            };
            let mut instant = round_trip(&instant, instant_model);
            let mut curve = round_trip(&curve, two_head);
            assert_eq!(recall(&mut restored), recall(&mut instant), "{two_head}");
            assert_eq!(
                intervals(&mut restored),
                intervals(&mut curve),
                "{two_head}"
            );
        }
    }
}
