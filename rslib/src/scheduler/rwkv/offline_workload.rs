// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Deck Options' "Compare RWKV with FSRS" and RWKV workload simulation
//! without the desktop's Python (`simulate_rwkv_workload` and
//! `compare_rwkv_and_fsrs_workload` in `qt/aqt/rwkv_scheduler.py`): the same
//! inputs, sampling, background reviews and answer model, given to the same
//! Rust simulation.
//!
//! It runs on its own thread with the runtime lent to it through [BUILDING],
//! so the collection is not held: until it is back the standard order
//! applies, as during a rebuild. The page polls the progress and the result.

use std::collections::BTreeMap;
use std::io;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::AtomicU32;

use anki_proto::collection::ComputeRetentionProgress;
use anki_proto::scheduler::rwkv_comparison_options::Learner;
use anki_proto::scheduler::rwkv_comparison_options::PerformanceScenario;
use anki_proto::scheduler::RwkvComparisonResponse;
use anki_proto::scheduler::RwkvOfflineWorkloadResultResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForSearchRequest;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsWorkloadResponse;

use super::*;
use crate::rwkv::RwkvComparisonBackgroundReview;
use crate::rwkv::RwkvComparisonCard;
use crate::rwkv::RwkvComparisonConfig;
use crate::rwkv::RwkvComparisonLearner;
use crate::rwkv::RwkvPerformanceScenario;
use crate::rwkv::RwkvWorkloadReviewModel;
use crate::rwkv::RwkvWorkloadSimulationConfig;
use crate::rwkv::RwkvWorkloadSimulationInput;
use crate::rwkv::RwkvWorkloadSimulationOutput;

const MIN_DR: u32 = 30;
const MAX_DR: u32 = 99;
const BUCKET_COUNT: usize = 20;
const PRIOR_WEIGHT: f64 = 4.0;
const DEFAULT_GRADE_SECONDS: f64 = 8.0;
const MAX_TAKEN_MILLIS: i64 = 600_000;
/// The desktop's `_rwkv_historical_answer_sql_condition`.
const ANSWER_SQL: &str = "select id, ease, time from revlog where ease between 1 and 4 \
     and type in (0, 1, 2, 3, 4, 5) and not (type = 3 and factor = 0)";

static RUNNING: AtomicBool = AtomicBool::new(false);
static CANCEL: AtomicBool = AtomicBool::new(false);
static PROGRESS_DONE: AtomicU32 = AtomicU32::new(0);
static PROGRESS_TOTAL: AtomicU32 = AtomicU32::new(0);
/// The finished job: the encoded response, or the error's message.
static RESULT: Mutex<Option<std::result::Result<Vec<u8>, String>>> = Mutex::new(None);

type Progress<'a> = &'a mut dyn FnMut(u32, u32) -> io::Result<()>;
type Job = Box<dyn FnOnce(&Engine, Progress) -> io::Result<Vec<u8>> + Send>;

fn set_progress(done: u32, total: u32) {
    PROGRESS_TOTAL.store(total, Ordering::Relaxed);
    PROGRESS_DONE.store(done, Ordering::Relaxed);
}

impl Collection {
    /// Without RWKV-Curve's model (RWKV-Instant alone runs the size's
    /// Instant model) there is nothing to simulate with, as on the desktop.
    pub(crate) fn rwkv_offline_workload_available(&mut self) -> bool {
        registered_model().is_none_or(|model| !model.instant_only)
    }

    pub(crate) fn rwkv_offline_workload_start(
        &mut self,
        request: SimulateFsrsReviewRequest,
    ) -> Result<()> {
        require!(
            !RUNNING.load(Ordering::Acquire),
            "an RWKV workload simulation is already running"
        );
        if !self.rwkv_offline_workload_available() {
            invalid_input!("{}", self.tr.deck_config_rwkv_workload_needs_curve());
        }
        if self.state.rwkv_offline.is_none() {
            self.state.rwkv_offline = self.rwkv_offline_take_built();
        }
        let Some(mut runtime) = self.state.rwkv_offline.take() else {
            invalid_input!("RWKV is not ready yet. Try again once it is ready.");
        };
        let job = self
            .rwkv_offline_sync_history(&mut runtime)
            .and_then(|_| self.rwkv_offline_workload_job(&runtime, &request));
        let mut building = BUILDING.lock().unwrap();
        let job = match job {
            Ok(job) if building.is_none() && runtime.identity.is_some() => job,
            other => {
                // without identity it is being rebuilt (a changed history)
                if runtime.identity.is_some() {
                    self.state.rwkv_offline = Some(runtime);
                }
                let _job = other?;
                invalid_input!("RWKV is being rebuilt. Try again once it is ready.");
            }
        };
        *RESULT.lock().unwrap() = None;
        CANCEL.store(false, Ordering::Relaxed);
        set_progress(0, 0);
        RUNNING.store(true, Ordering::Release);
        let handle = std::thread::spawn(move || -> Result<Box<RwkvOfflineRuntime>> {
            let started = Instant::now();
            let mut progress = |done: u32, total: u32| -> io::Result<()> {
                if CANCEL.load(Ordering::Relaxed) {
                    return Err(io::Error::other("RWKV workload simulation interrupted"));
                }
                set_progress(done, total);
                Ok(())
            };
            let result = job(&runtime.inference, &mut progress).map_err(|err| err.to_string());
            tracing::info!(
                ok = result.is_ok(),
                ms = started.elapsed().as_millis() as u64,
                "RWKV offline workload simulation done"
            );
            *RESULT.lock().unwrap() = Some(result);
            RUNNING.store(false, Ordering::Release);
            Ok(runtime)
        });
        *building = Some((self.col_path.clone(), handle));
        Ok(())
    }

    /// `done` is false while it runs; then `result` holds the encoded
    /// response, or `error` why there is none.
    pub(crate) fn rwkv_offline_workload_result(
        &mut self,
    ) -> Result<RwkvOfflineWorkloadResultResponse> {
        // RUNNING first: the thread stores RESULT before clearing it
        let running = RUNNING.load(Ordering::Acquire);
        let result = RESULT.lock().unwrap().take();
        if self.state.rwkv_offline.is_none() {
            self.state.rwkv_offline = self.rwkv_offline_take_built();
        }
        let mut out = RwkvOfflineWorkloadResultResponse::default();
        match result {
            Some(Ok(bytes)) => (out.done, out.result) = (true, bytes),
            Some(Err(error)) => (out.done, out.error) = (true, error),
            None if running => {}
            None => {
                out.done = true;
                out.error = "RWKV workload simulation has not been started".into();
            }
        }
        Ok(out)
    }

    pub(crate) fn rwkv_offline_workload_cancel(&mut self) {
        if RUNNING.load(Ordering::Acquire) {
            CANCEL.store(true, Ordering::Relaxed);
        }
    }

    pub(crate) fn rwkv_offline_workload_progress(&mut self) -> ComputeRetentionProgress {
        ComputeRetentionProgress {
            current: PROGRESS_DONE.load(Ordering::Relaxed),
            total: PROGRESS_TOTAL.load(Ordering::Relaxed),
        }
    }

    /// Everything that needs the collection; the returned job only needs the
    /// model.
    fn rwkv_offline_workload_job(
        &mut self,
        runtime: &RwkvOfflineRuntime,
        request: &SimulateFsrsReviewRequest,
    ) -> Result<Job> {
        let comparison = request.rwkv_comparison.as_ref();
        let rows = self
            .rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search: request.search.clone(),
                include_suspended_review: false,
                include_disabled_decks: false,
                include_new_cards: request.new_limit > 0,
                include_comparison_data: comparison.is_some(),
            })?
            .rows;
        let all = simulation_rows(rows);
        let total_cards = all.len();
        let sample_limit = request.rwkv_workload_sample_limit as usize;
        let sampled = sample(all, sample_limit);
        let input_scale = if sampled.is_empty() {
            1.0
        } else {
            total_cards as f64 / sampled.len() as f64
        };
        let model = self.rwkv_offline_workload_review_model(runtime)?;
        let step = request.rwkv_workload_target_step.max(1);
        let review_order = request.review_order();
        let suspend_after_lapses = request.suspend_after_lapse_count;
        let max_interval = request.max_interval.max(1);

        let Some(options) = comparison else {
            // simulate_rwkv_workload
            let review_limit = request.review_limit;
            let new_limit = request.new_limit;
            let cap = review_count_cap(
                review_limit,
                if request.new_cards_ignore_review_limit {
                    new_limit
                } else {
                    0
                },
                request.days_to_simulate,
            );
            let config = RwkvWorkloadSimulationConfig {
                min_dr: MIN_DR,
                max_dr: MAX_DR,
                target_dr_step: step,
                days_to_simulate: request.days_to_simulate,
                review_limit: sampled_limit(review_limit, input_scale),
                new_limit: sampled_limit(new_limit, input_scale),
                new_cards_ignore_review_limit: request.new_cards_ignore_review_limit,
                max_interval,
                review_order,
                suspend_after_lapses,
                state_update_interval: request.rwkv_workload_state_update_interval.max(1),
                review_model: model.engine_model(),
            };
            let inputs: Vec<_> = sampled
                .iter()
                .map(|(row, input)| sim_input(row, input))
                .collect();
            return Ok(Box::new(move |engine, progress| {
                let mut response = SimulateFsrsWorkloadResponse::default();
                if inputs.is_empty() {
                    return Ok(response.encode_to_vec());
                }
                model.apply_review_time(&mut response);
                let total = target_drs(MIN_DR, MAX_DR, step).len() as u32 + 1;
                progress(0, total)?;
                let output = engine.simulate_workload_from_warm_up(inputs, config, progress)?;
                apply_output(&mut response, output);
                scale_response(&mut response, input_scale, cap);
                set_progress(total, total);
                Ok(response.encode_to_vec())
            }));
        };

        // compare_rwkv_and_fsrs_workload
        let repetitions = options.repetitions.max(1);
        require!(
            repetitions <= 100,
            "Comparison repetitions must be between 1 and 100"
        );
        let mut learners = vec![match options.learner() {
            Learner::Fsrs => RwkvComparisonLearner::Fsrs,
            Learner::Rwkv => RwkvComparisonLearner::Rwkv,
        }];
        if options.compare_both_learners {
            learners.push(match learners[0] {
                RwkvComparisonLearner::Fsrs => RwkvComparisonLearner::Rwkv,
                RwkvComparisonLearner::Rwkv => RwkvComparisonLearner::Fsrs,
            });
        }
        let scenario = match options.scenario() {
            PerformanceScenario::Steady => RwkvPerformanceScenario::Steady,
            PerformanceScenario::TemporaryDrop => RwkvPerformanceScenario::TemporaryDrop,
            PerformanceScenario::PersistentDrop => RwkvPerformanceScenario::PersistentDrop,
            PerformanceScenario::TemporaryImprovement => {
                RwkvPerformanceScenario::TemporaryImprovement
            }
        };
        let background = if sampled.is_empty() {
            Vec::new()
        } else {
            self.rwkv_offline_comparison_background(&sampled)?
        };
        let cards: Vec<_> = sampled
            .iter()
            .map(|(row, input)| comparison_card(row, input, request))
            .collect();
        let (sampled_cards, total_cards) = (cards.len() as u32, total_cards as u32);
        let review_limit = request.review_limit as f32 / input_scale as f32;
        let new_limit = request.new_limit as f32 / input_scale as f32;
        let new_cards_ignore_review_limit = request.new_cards_ignore_review_limit;
        let days_to_simulate = request.days_to_simulate;
        Ok(Box::new(move |engine, progress| {
            let per_learner = 1 + target_drs(MIN_DR, MAX_DR, step).len() as u32 * repetitions * 2;
            let total = per_learner * learners.len() as u32;
            progress(0, total)?;
            let background_reviews = background.len() as u32;
            let mut responses = Vec::new();
            for (index, &learner) in learners.iter().enumerate() {
                let mut response = RwkvComparisonResponse {
                    total_cards,
                    sampled_cards,
                    ..Default::default()
                };
                if cards.is_empty() {
                    let empty = Some(SimulateFsrsWorkloadResponse::default());
                    response.fsrs = empty.clone();
                    response.rwkv = empty.clone();
                    response.fsrs_scored_by_fsrs = empty.clone();
                    response.rwkv_scored_by_fsrs = empty.clone();
                    response.fsrs_scored_by_rwkv = empty.clone();
                    response.rwkv_scored_by_rwkv = empty;
                    responses.push(response);
                    continue;
                }
                let config = RwkvComparisonConfig {
                    min_dr: MIN_DR,
                    max_dr: MAX_DR,
                    target_dr_step: step,
                    days_to_simulate,
                    review_limit,
                    new_limit,
                    new_cards_ignore_review_limit,
                    max_interval,
                    review_order,
                    suspend_after_lapses,
                    learner,
                    scenario,
                    repetitions,
                    output_scale: input_scale as f32,
                    review_model: model.engine_model(),
                    background: background.clone(),
                };
                let offset = index as u32 * per_learner;
                let output = engine.compare_workloads_from_warm_up(
                    cards.clone(),
                    config,
                    &mut |done: u32, _: u32| progress(offset + done, total),
                )?;
                let to_response = |output| {
                    let mut response = SimulateFsrsWorkloadResponse::default();
                    apply_output(&mut response, output);
                    Some(response)
                };
                response.fsrs_scored_by_fsrs = to_response(output.fsrs_scored_by_fsrs);
                response.rwkv_scored_by_fsrs = to_response(output.rwkv_scored_by_fsrs);
                response.fsrs_scored_by_rwkv = to_response(output.fsrs_scored_by_rwkv);
                response.rwkv_scored_by_rwkv = to_response(output.rwkv_scored_by_rwkv);
                (response.fsrs, response.rwkv) = match learner {
                    RwkvComparisonLearner::Rwkv => (
                        response.fsrs_scored_by_rwkv.clone(),
                        response.rwkv_scored_by_rwkv.clone(),
                    ),
                    RwkvComparisonLearner::Fsrs => (
                        response.fsrs_scored_by_fsrs.clone(),
                        response.rwkv_scored_by_fsrs.clone(),
                    ),
                };
                response.estimated_fsrs_states = output.estimated_fsrs_states;
                response.background_reviews = background_reviews;
                responses.push(response);
            }
            set_progress(total, total);
            let mut responses = responses.into_iter();
            let mut response = responses.next().unwrap_or_default();
            response.other_learner = responses.next().map(Box::new);
            Ok(response.encode_to_vec())
        }))
    }

    /// The desktop's `_rwkv_comparison_background`: the last 365 complete
    /// days of answers to the cards not simulated, repeated, each starting
    /// from its card's latest real answer.
    fn rwkv_offline_comparison_background(
        &mut self,
        sampled: &[(Row, ReviewInput)],
    ) -> Result<Vec<RwkvComparisonBackgroundReview>> {
        let timing = self.timing_today()?;
        let today = timing.days_elapsed as i64;
        let day_start_seconds = timing.next_day_at.0 - 86_400;
        let request = self.rwkv_offline_history_request()?;
        let history = self.read_rwkv_historical_reviews(request, Default::default())?;
        let mut reviews = Vec::new();
        let _fingerprint = history
            .walk(|review, _, _| reviews.push(super::super::history::historical_review(&review)))?;
        let sampled: HashSet<i64> = sampled.iter().map(|(row, _)| row.card_id).collect();
        // card -> (its latest answer's day relative to today, second, input)
        let mut latest: HashMap<i64, (i64, i64, &Review)> = HashMap::new();
        for review in &reviews {
            latest.insert(
                review.card_id,
                (review.day_offset - today, review.review_id / 1000, review),
            );
        }
        let start = today - 365;
        let mut background = Vec::new();
        for review in &reviews {
            let day = review.day_offset;
            if sampled.contains(&review.card_id) || day < start || day >= today {
                continue;
            }
            let (last_day, last_seconds, identity) = latest[&review.card_id];
            let mut input = answered_input(review);
            input.note_id = Some(identity.note_id);
            input.deck_id = Some(identity.deck_id);
            input.preset_id = Some(identity.preset_id);
            input.day_offset = Some(today);
            background.push(RwkvComparisonBackgroundReview {
                input,
                pattern_day: (day - start) as u32,
                second_of_day: (review.review_id / 1000 - day_start_seconds).rem_euclid(86_400)
                    as u32,
                last_review_day: last_day,
                last_review_second: last_seconds - day_start_seconds,
            });
        }
        Ok(background)
    }

    /// The desktop's `_rwkv_simulator_review_model`, from the recall RWKV
    /// predicted before each answer ([Calibration]: the newest 30% of the
    /// history or all of it). Without one, the defaults.
    fn rwkv_offline_workload_review_model(
        &mut self,
        runtime: &RwkvOfflineRuntime,
    ) -> Result<ReviewModel> {
        let mut samples = Vec::new();
        if let Some(calibration) = &runtime.calibration {
            let predictions: HashMap<i64, f32> = calibration.predictions.iter().copied().collect();
            let mut stmt = self.storage.db.prepare(ANSWER_SQL)?;
            let mut rows = stmt.query([])?;
            while let Some(row) = rows.next()? {
                let id: i64 = row.get(0)?;
                if let Some(&prediction) = predictions.get(&id) {
                    samples.push((prediction as f64, row.get::<_, i64>(1)?, row.get(2)?));
                }
            }
        } else {
            tracing::info!("RWKV workload simulation without calibration: default answer model");
        }
        Ok(ReviewModel::from_samples(&samples))
    }
}

/// The desktop's `_rwkv_simulation_inputs`: new cards, and review cards
/// with a last answer.
fn simulation_rows(rows: Vec<Row>) -> Vec<(Row, ReviewInput)> {
    rows.into_iter()
        .filter_map(|row| {
            let input = query_input(&row);
            match input.card_type {
                Some(0) => Some((row, input)),
                Some(2) if input.current_elapsed_days.is_some() => Some((row, input)),
                _ => None,
            }
        })
        .collect()
}

/// The desktop's `_sample_rwkv_simulation_inputs`: evenly spread over the
/// cards in id order.
fn sample<T>(mut inputs: Vec<(Row, T)>, limit: usize) -> Vec<(Row, T)> {
    if limit == 0 || inputs.len() <= limit {
        return inputs;
    }
    inputs.sort_by_key(|(row, _)| row.card_id);
    let total = inputs.len();
    let picks: Vec<usize> = if limit == 1 {
        vec![total / 2]
    } else {
        (0..limit)
            .map(|index| {
                // Python's round(): ties to even
                (index as f64 * (total - 1) as f64 / (limit - 1) as f64).round_ties_even() as usize
            })
            .collect()
    };
    let mut inputs: Vec<Option<(Row, T)>> = inputs.into_iter().map(Some).collect();
    picks
        .into_iter()
        .filter_map(|index| inputs.get_mut(index).and_then(Option::take))
        .collect()
}

fn sim_input(row: &Row, input: &ReviewInput) -> RwkvWorkloadSimulationInput {
    RwkvWorkloadSimulationInput {
        review_input: input.clone(),
        interval_days: Some(row.interval_days.into()),
        ease_factor: Some(row.ease_factor.into()),
        reps: Some(row.reps.into()),
        lapses: Some(row.lapses.into()),
    }
}

fn comparison_card(
    row: &Row,
    input: &ReviewInput,
    request: &SimulateFsrsReviewRequest,
) -> RwkvComparisonCard {
    let fsrs_memory = row.fsrs_memory_state.as_ref().map(|memory| {
        let stability = memory.stability_internal.unwrap_or(memory.stability);
        (
            stability,
            memory.difficulty,
            memory.stability_fast.unwrap_or(stability),
        )
    });
    let fsrs_parameters = if row.fsrs_parameters.is_empty() {
        request.params.clone()
    } else {
        row.fsrs_parameters.clone()
    };
    // Python's `a or b or 0.9`: 0 counts as missing
    let historical_retention = [
        row.fsrs_historical_retention.unwrap_or(0.0),
        request.historical_retention,
    ]
    .into_iter()
    .find(|retention| *retention != 0.0)
    .unwrap_or(0.9);
    RwkvComparisonCard {
        input: sim_input(row, input),
        due_day: row.card_due as i64 - row.day_offset as i64,
        fsrs_memory,
        fsrs_parameters,
        historical_retention,
    }
}

fn target_drs(min_dr: u32, max_dr: u32, step: u32) -> Vec<u32> {
    if min_dr > max_dr {
        return Vec::new();
    }
    let mut values: Vec<u32> = (min_dr..=max_dr).step_by(step.max(1) as usize).collect();
    if values.last() != Some(&max_dr) {
        values.push(max_dr);
    }
    values
}

fn sampled_limit(limit: u32, input_scale: f64) -> u32 {
    if limit == 0 {
        0
    } else if input_scale <= 1.0 {
        limit
    } else {
        ((limit as f64 / input_scale).round_ties_even() as u32).max(1)
    }
}

fn review_count_cap(review_limit: u32, additional_new_limit: u32, days: u32) -> u64 {
    ((review_limit as u64 + additional_new_limit as u64) * days as u64).min(u32::MAX as u64)
}

fn apply_output(response: &mut SimulateFsrsWorkloadResponse, output: RwkvWorkloadSimulationOutput) {
    response.reviewless_end_memorized = output.reviewless_end_memorized;
    response.reviewless_end_weighted_memorized = output.reviewless_end_weighted_memorized;
    for (dr, point) in output.points {
        response.memorized.insert(dr, point.memorized);
        response
            .weighted_memorized
            .insert(dr, point.weighted_memorized);
        response.cost.insert(dr, point.cost);
        response.review_count.insert(dr, point.review_count);
    }
}

/// The desktop's `_scale_rwkv_workload_response`.
fn scale_response(response: &mut SimulateFsrsWorkloadResponse, scale: f64, cap: u64) {
    if scale != 1.0 {
        let scale32 = scale as f32;
        response.reviewless_end_memorized *= scale32;
        response.reviewless_end_weighted_memorized *= scale32;
        for map in [
            &mut response.memorized,
            &mut response.weighted_memorized,
            &mut response.cost,
        ] {
            for value in map.values_mut() {
                *value *= scale32;
            }
        }
    }
    for value in response.review_count.values_mut() {
        let count = (*value as f64 * scale).round_ties_even().max(0.0) as u64;
        *value = count.min(cap).min(u32::MAX as u64) as u32;
    }
}

fn bucket(retrievability: f64) -> usize {
    if !retrievability.is_finite() {
        return BUCKET_COUNT - 1;
    }
    ((retrievability * BUCKET_COUNT as f64).max(0.0) as usize).min(BUCKET_COUNT - 1)
}

fn ui_bucket(retrievability: f64) -> usize {
    if !retrievability.is_finite() {
        return BUCKET_COUNT - 1;
    }
    let clamped = retrievability.clamp(0.0, 1.0);
    BUCKET_COUNT - 1 - ((clamped * 100.0).min(99.9999) / 5.0) as usize
}

/// The desktop's `_fallback_rwkv_grade_probabilities`.
fn fallback_probabilities(retrievability: f64) -> [f64; 4] {
    let r = if retrievability.is_finite() {
        retrievability.clamp(0.0, 1.0)
    } else {
        0.9
    };
    let again = (1.0 - r).clamp(0.02, 0.85);
    let success = 1.0 - again;
    let mut hard_share = ((0.95 - r) / 0.45).clamp(0.10, 0.45);
    let mut easy_share = ((r - 0.75) / 0.25).clamp(0.05, 0.35);
    if hard_share + easy_share > 0.90 {
        let scale = 0.90 / (hard_share + easy_share);
        hard_share *= scale;
        easy_share *= scale;
    }
    let hard = success * hard_share;
    let easy = success * easy_share;
    let good = (success - hard - easy).max(0.0);
    let total = again + hard + good + easy;
    [again / total, hard / total, good / total, easy / total]
}

fn bucket_retrievability(bucket: usize) -> f64 {
    (1.0 - (bucket as f64 + 0.5) * 0.05).clamp(0.0, 1.0)
}

/// The desktop's `_RwkvSimulatorReviewModel`: grade probabilities per
/// recall bucket and answer times, for the simulation and the page's
/// review-time fields.
#[derive(Default)]
struct ReviewModel {
    grade_seconds: [f64; 4],
    bucket_probabilities: Vec<(u32, [f64; 4])>,
    per_grade_seconds: [Vec<f32>; 4],
    sample_counts: Vec<u32>,
    coeffs: [[f64; 5]; 4],
    grade_weights: [f64; 4],
    success_grade_probs: Vec<f32>,
    success_grade_counts: Vec<u32>,
}

impl ReviewModel {
    /// `samples`: (predicted recall, ease, milliseconds taken).
    fn from_samples(samples: &[(f64, i64, i64)]) -> Self {
        let mut bucket_counts: BTreeMap<usize, [f64; 4]> = BTreeMap::new();
        let mut seconds_sum = [0.0; 4];
        let mut seconds_count = [0u32; 4];
        let mut timed: Vec<(f64, usize, f64)> = Vec::new();
        for &(retrievability, ease, taken) in samples {
            if !(1..=4).contains(&ease) || !(0.0..=1.0).contains(&retrievability) {
                continue;
            }
            let grade = ease as usize - 1;
            bucket_counts.entry(bucket(retrievability)).or_default()[grade] += 1.0;
            if 0 < taken && taken < MAX_TAKEN_MILLIS {
                let seconds = taken as f64 / 1000.0;
                seconds_sum[grade] += seconds;
                seconds_count[grade] += 1;
                timed.push((retrievability, grade, seconds));
            }
        }
        let bucket_probabilities = bucket_counts
            .iter()
            .filter_map(|(&bucket, counts)| {
                let fallback = fallback_probabilities((bucket as f64 + 0.5) / BUCKET_COUNT as f64);
                let smoothed: Vec<f64> = (0..4)
                    .map(|i| counts[i] + fallback[i] * PRIOR_WEIGHT)
                    .collect();
                let total: f64 = smoothed.iter().sum();
                (total > 0.0).then(|| (bucket as u32, std::array::from_fn(|i| smoothed[i] / total)))
            })
            .collect();
        let grade_seconds: [f64; 4] = std::array::from_fn(|i| {
            if seconds_count[i] > 0 {
                seconds_sum[i] / seconds_count[i] as f64
            } else {
                DEFAULT_GRADE_SECONDS
            }
        });

        // _rwkv_simulator_review_time_fields
        let mut by_grade: [Vec<(f64, f64)>; 4] = Default::default();
        let mut sample_counts = vec![0u32; BUCKET_COUNT];
        let mut success_counts = vec![[0.0f64; 3]; BUCKET_COUNT];
        for &(retrievability, grade, seconds) in &timed {
            let bucket = ui_bucket(retrievability);
            sample_counts[bucket] += 1;
            by_grade[grade].push((retrievability, seconds));
            if grade > 0 {
                success_counts[bucket][grade - 1] += 1.0;
            }
        }
        let coeffs: [[f64; 5]; 4] =
            std::array::from_fn(|i| time_coeffs(&by_grade[i], grade_seconds[i]));
        let per_grade_seconds = std::array::from_fn(|i| {
            (0..BUCKET_COUNT)
                .map(|bucket| {
                    let predicted =
                        coeffs[i][0] + coeffs[i][1] * (1.0 - bucket_retrievability(bucket));
                    if predicted.is_finite() && predicted > 0.0 {
                        predicted as f32
                    } else {
                        grade_seconds[i] as f32
                    }
                })
                .collect()
        });
        let mut totals = [0.0; 4];
        for counts in bucket_counts.values() {
            for i in 0..4 {
                totals[i] += counts[i];
            }
        }
        let total: f64 = totals.iter().sum();
        let grade_weights = if total <= 0.0 {
            [0.25; 4]
        } else {
            std::array::from_fn(|i| totals[i] / total)
        };
        let success_grade_probs = (0..BUCKET_COUNT)
            .flat_map(|bucket| {
                let fallback = fallback_probabilities(bucket_retrievability(bucket));
                let fallback_total: f64 = fallback[1..].iter().sum();
                let prior: [f64; 3] = if fallback_total > 0.0 {
                    std::array::from_fn(|i| fallback[i + 1] / fallback_total)
                } else {
                    [1.0 / 3.0; 3]
                };
                let smoothed: [f64; 3] =
                    std::array::from_fn(|i| success_counts[bucket][i] + prior[i] * PRIOR_WEIGHT);
                let total: f64 = smoothed.iter().sum();
                let probs = if total <= 0.0 {
                    prior
                } else {
                    smoothed.map(|value| value / total)
                };
                probs.map(|p| p as f32)
            })
            .collect();
        Self {
            grade_seconds,
            bucket_probabilities,
            per_grade_seconds,
            sample_counts,
            coeffs,
            grade_weights,
            success_grade_probs,
            success_grade_counts: success_counts
                .iter()
                .map(|counts| counts.iter().sum::<f64>() as u32)
                .collect(),
        }
    }

    fn engine_model(&self) -> RwkvWorkloadReviewModel {
        RwkvWorkloadReviewModel {
            grade_seconds: self.grade_seconds.map(|s| s as f32),
            bucket_probabilities: self
                .bucket_probabilities
                .iter()
                .map(|(bucket, probs)| (*bucket, probs.map(|p| p as f32)))
                .collect(),
        }
    }

    /// The desktop's `_apply_rwkv_review_time_model`.
    fn apply_review_time(&self, response: &mut SimulateFsrsWorkloadResponse) {
        let coeffs = |i: usize| self.coeffs[i].iter().map(|&c| c as f32).collect();
        response.review_time_r_bucket_count = BUCKET_COUNT as u32;
        response.review_time_s_bucket_count = 1;
        response.review_time_again_seconds = self.per_grade_seconds[0].clone();
        response.review_time_hard_seconds = self.per_grade_seconds[1].clone();
        response.review_time_good_seconds = self.per_grade_seconds[2].clone();
        response.review_time_easy_seconds = self.per_grade_seconds[3].clone();
        response.review_time_sample_counts = self.sample_counts.clone();
        response.review_time_again_coeffs = coeffs(0);
        response.review_time_hard_coeffs = coeffs(1);
        response.review_time_good_coeffs = coeffs(2);
        response.review_time_easy_coeffs = coeffs(3);
        response.review_time_grade_weights = self.grade_weights.map(|w| w as f32).to_vec();
        response.review_time_transition_probs = (0..4)
            .flat_map(|_| self.grade_weights.map(|w| w as f32))
            .collect();
        response.review_time_transition_counts = vec![0; 16];
        response.review_time_success_grade_probs = self.success_grade_probs.clone();
        response.review_time_success_grade_counts = self.success_grade_counts.clone();
    }
}

/// The desktop's `_rwkv_simulator_review_time_coeffs`: answer seconds as a
/// line over 1 - recall.
fn time_coeffs(samples: &[(f64, f64)], fallback_seconds: f64) -> [f64; 5] {
    if samples.len() < 2 {
        return [fallback_seconds, 0.0, 0.0, 0.0, 0.0];
    }
    let xs: Vec<f64> = samples
        .iter()
        .map(|(r, _)| 1.0 - r.clamp(0.0, 1.0))
        .collect();
    let ys: Vec<f64> = samples.iter().map(|(_, s)| *s).collect();
    let n = xs.len() as f64;
    let mean_x = xs.iter().sum::<f64>() / n;
    let mean_y = ys.iter().sum::<f64>() / n;
    let denominator: f64 = xs.iter().map(|x| (x - mean_x).powi(2)).sum();
    if denominator <= 1e-9 {
        return [mean_y, 0.0, 0.0, 0.0, 0.0];
    }
    let slope = xs
        .iter()
        .zip(&ys)
        .map(|(x, y)| (x - mean_x) * (y - mean_y))
        .sum::<f64>()
        / denominator;
    let intercept = mean_y - slope * mean_x;
    if intercept.is_finite() && slope.is_finite() {
        [intercept, slope, 0.0, 0.0, 0.0]
    } else {
        [fallback_seconds, 0.0, 0.0, 0.0, 0.0]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn row(card_id: i64) -> Row {
        Row {
            card_id,
            ..Default::default()
        }
    }

    #[test]
    fn sampling_spreads_over_card_ids_like_the_desktop() {
        let rows: Vec<_> = (0..10).rev().map(|id| (row(id), ())).collect();
        let ids = |rows: Vec<(Row, ())>| rows.iter().map(|(r, _)| r.card_id).collect::<Vec<_>>();
        assert_eq!(ids(sample(rows.clone(), 0)).len(), 10);
        assert_eq!(ids(sample(rows.clone(), 1)), vec![5]);
        // round(i * 9 / 3): 0, 3, 6, 9
        assert_eq!(ids(sample(rows.clone(), 4)), vec![0, 3, 6, 9]);
        // round(i * 9 / 4) = 0, 2.25, 4.5, 6.75, 9 -> ties to even: 4
        assert_eq!(ids(sample(rows, 5)), vec![0, 2, 4, 7, 9]);
    }

    #[test]
    fn targets_limits_and_buckets_match_the_desktop() {
        assert_eq!(target_drs(30, 99, 10), vec![30, 40, 50, 60, 70, 80, 90, 99]);
        assert_eq!(target_drs(30, 99, 1).len(), 70);
        assert_eq!(sampled_limit(9999, 4.0), 2500);
        assert_eq!(sampled_limit(1, 4.0), 1);
        assert_eq!(sampled_limit(0, 4.0), 0);
        assert_eq!(bucket(0.93), 18);
        assert_eq!(bucket(1.0), 19);
        assert_eq!(ui_bucket(1.0), 0);
        assert_eq!(ui_bucket(0.0), 19);
        let fallback = fallback_probabilities(0.9);
        assert!((fallback.iter().sum::<f64>() - 1.0).abs() < 1e-9);
        assert!((fallback[0] - 0.1).abs() < 1e-9);
    }

    #[test]
    fn review_model_smooths_counts_and_fits_answer_times() {
        let samples = [
            (0.95, 3, 4000),
            (0.95, 3, 6000),
            (0.2, 1, 20_000),
            (0.2, 1, 30_000),
        ];
        let model = ReviewModel::from_samples(&samples);
        assert_eq!(model.grade_seconds, [25.0, 8.0, 5.0, 8.0]);
        let (bucket, probs) = model.bucket_probabilities[1];
        assert_eq!(bucket, 19);
        // 2 Goods + 4 x the fallback's share, over 6
        let fallback = fallback_probabilities(0.975);
        assert!((probs[2] - (2.0 + 4.0 * fallback[2]) / 6.0).abs() < 1e-9);
        assert_eq!(model.grade_weights, [0.5, 0.0, 0.5, 0.0]);
        assert_eq!(model.sample_counts.iter().sum::<u32>(), 4);
        let empty = ReviewModel::from_samples(&[]);
        assert_eq!(empty.grade_seconds, [8.0; 4]);
        assert!(empty.bucket_probabilities.is_empty());
        assert_eq!(empty.success_grade_probs.len(), 60);
    }

    #[test]
    fn scaling_caps_review_counts() {
        let mut response = SimulateFsrsWorkloadResponse::default();
        response.review_count.insert(90, 10);
        response.cost.insert(90, 2.0);
        scale_response(&mut response, 2.5, 20);
        assert_eq!(response.review_count[&90], 20);
        assert_eq!(response.cost[&90], 5.0);
    }
}
