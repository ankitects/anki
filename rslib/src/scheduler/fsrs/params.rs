// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;
use std::collections::HashSet;
use std::iter;
use std::path::Path;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::AtomicU8;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::sync::Mutex;
use std::thread;
use std::time::Duration;

use anki_io::write_file;
use anki_proto::scheduler::ComputeFsrsParamsResponse;
use anki_proto::stats::revlog_entry;
use anki_proto::stats::Dataset;
use anki_proto::stats::DeckEntry;
use chrono::NaiveDate;
use chrono::NaiveTime;
use fsrs::compute_parameters;
use fsrs::evaluate_with_time_series_splits;
use fsrs::extract_simulator_config;
use fsrs::CardPrefixes;
use fsrs::CombinedProgressState;
use fsrs::ComputeParametersInput;
use fsrs::ComputeParametersVersion;
use fsrs::CostAdrEvaluationPoint;
use fsrs::CostAdrMetrics;
use fsrs::CostAdrPolicy;
use fsrs::CostAdrTrainingConfig;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::ModelEvaluation;
use fsrs::ModelVersion;
use fsrs::SimulatorConfig;
use fsrs::FSRS;
use itertools::Itertools;
use prost::Message;

mod compact;

use compact::compact_fsrs_items_for_training;
use compact::compact_fsrs_items_for_training_with_progress;
use compact::compute_compact_parameters;
use compact::fsrs_review_retrievability_predictions;
use compact::CompactTrainingItems;

use super::legacy_fsrs_params;
use crate::card::Card;
use crate::decks::immediate_parent_name;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::fsrs::dynamic_desired_retention::DEFAULT_RETENTION_MAX;
use crate::scheduler::fsrs::dynamic_desired_retention::DEFAULT_RETENTION_MIN;
use crate::scheduler::fsrs::memory_state::fsrs_items_for_memory_states;
use crate::scheduler::fsrs::review_time_model::build_help_me_decide_review_time_model_from_revlogs;
use crate::scheduler::fsrs::review_time_model::install_review_time_cost_fn;
use crate::scheduler::fsrs::simulator::is_included_card;
use crate::search::Node;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::storage::FsrsReviewRetrievabilityCacheRow;
use crate::storage::FsrsReviewRetrievabilitySampleRole;

pub(crate) type Params = Vec<f32>;

const FSRS_VALIDATION_FOLDS: usize = 5;
const FSRS_CALIBRATION_PROGRESS_SCALE: usize = 1000;

fn model_version_for_params(params: &[f32]) -> ComputeParametersVersion {
    if super::uses_fractional_intervals(params) {
        ComputeParametersVersion::Fsrs7
    } else {
        ComputeParametersVersion::Fsrs6
    }
}

fn include_same_day_training_entries(
    model_version: ComputeParametersVersion,
    include_same_day_override: Option<bool>,
) -> bool {
    match model_version {
        ComputeParametersVersion::Fsrs7 => include_same_day_override.unwrap_or(true),
        ComputeParametersVersion::Fsrs6 => false,
    }
}

pub(crate) fn include_same_day_for_params(params: &[f32]) -> bool {
    include_same_day_training_entries(model_version_for_params(params), None)
}

pub(crate) fn ignore_revlogs_before_date_to_ms(
    ignore_revlogs_before_date: &String,
) -> Result<TimestampMillis> {
    Ok(match ignore_revlogs_before_date {
        s if s.is_empty() => 0,
        s => NaiveDate::parse_from_str(s.as_str(), "%Y-%m-%d")
            .or_else(|err| invalid_input!(err, "Error parsing date: {s}"))?
            .and_time(NaiveTime::from_hms_milli_opt(0, 0, 0, 0).unwrap())
            .and_utc()
            .timestamp_millis(),
    }
    .into())
}

pub(crate) fn ignore_revlogs_before_ms_from_config(config: &DeckConfig) -> Result<TimestampMillis> {
    ignore_revlogs_before_date_to_ms(&config.inner.ignore_revlogs_before_date)
}

pub struct ComputeParamsRequest<'t> {
    pub search: &'t str,
    pub ignore_revlogs_before_ms: TimestampMillis,
    pub current_preset: u32,
    pub total_presets: u32,
    pub current_params: &'t Params,
    pub num_of_relearning_steps: usize,
    pub health_check: bool,
    pub include_same_day_reviews: Option<bool>,
    pub enable_scheduling_penalties: bool,
    pub model_version_override: Option<ComputeParametersVersion>,
    pub dynamic_desired_retention_enabled: bool,
    pub dynamic_desired_retention_review_limit: Option<u32>,
    pub dynamic_desired_retention_max_cost_perday_minutes: Option<f32>,
}

pub(crate) struct PreparedComputeParams {
    pub current_params: Params,
    pub num_of_relearning_steps: usize,
    pub model_version: ComputeParametersVersion,
    pub include_same_day_reviews: bool,
    pub enable_scheduling_penalties: bool,
    pub dynamic_desired_retention_enabled: bool,
    pub simulator_config: SimulatorConfig,
    pub existing_card_input: Option<ExistingCardInput>,
    pub training: CompactTrainingItems,
    pub target_counts: TrainingTargetCounts,
}

pub(crate) struct ExistingCardInput {
    pub cards: Vec<Card>,
    pub revlogs: Vec<RevlogEntry>,
    pub next_day_at: TimestampSecs,
    pub days_elapsed: i32,
    pub ignore_revlogs_before: TimestampMillis,
    pub historical_retention: f32,
    pub desired_retention: f32,
}

pub(crate) struct PrepareComputeParamsInput<'a> {
    pub search: &'a str,
    pub ignore_revlogs_before: TimestampMillis,
    pub current_params: &'a [f32],
    pub num_of_relearning_steps: usize,
    pub include_same_day_reviews: Option<bool>,
    pub enable_scheduling_penalties: bool,
    pub model_version_override: Option<ComputeParametersVersion>,
    pub dynamic_desired_retention_enabled: bool,
    pub historical_retention: f32,
    pub desired_retention: f32,
    pub dynamic_desired_retention_simulator_options: DynamicDesiredRetentionSimulatorOptions,
}

struct DynamicDesiredRetentionCalibration {
    params: Vec<f32>,
    weights: Vec<f32>,
    avg_drs: Vec<f32>,
    fsrs_eq_weights: Vec<f32>,
    fsrs_eq_drs: Vec<f32>,
    fixed_target_weights: Vec<f32>,
    fixed_target_drs: Vec<f32>,
    retention_min: f32,
    retention_max: f32,
}

#[derive(Debug, Clone, Copy, PartialEq)]
struct DynamicDesiredRetentionBounds {
    retention_min: f32,
    retention_max: f32,
}

#[derive(Debug, Clone, Copy, Default)]
pub struct DynamicDesiredRetentionSimulatorOptions {
    pub review_limit: Option<u32>,
    pub max_cost_perday_minutes: Option<f32>,
}

#[derive(Default, Clone, Copy, Debug, PartialEq, Eq)]
pub enum ComputeParamsProgressPhase {
    #[default]
    OptimizingFsrsParams = 0,
    TrainingDynamicDesiredRetention = 1,
}

impl ComputeParamsProgressPhase {
    pub(crate) fn from_shared(progress_phase: &SharedComputeParamsProgressPhase) -> Self {
        match progress_phase.load(Ordering::Acquire) {
            1 => Self::TrainingDynamicDesiredRetention,
            _ => Self::OptimizingFsrsParams,
        }
    }
}

pub(crate) type SharedComputeParamsProgressPhase = Arc<AtomicU8>;

pub(crate) fn new_compute_params_progress_phase() -> SharedComputeParamsProgressPhase {
    Arc::new(AtomicU8::new(
        ComputeParamsProgressPhase::OptimizingFsrsParams as u8,
    ))
}

pub(crate) struct FsrsReviewRetrievabilityProgress {
    training_progress: Arc<Mutex<CombinedProgressState>>,
    completed_validation_folds: Arc<AtomicUsize>,
}

fn set_compute_params_progress_phase(
    progress_phase: Option<&SharedComputeParamsProgressPhase>,
    phase: ComputeParamsProgressPhase,
) {
    if let Some(progress_phase) = progress_phase {
        progress_phase.store(phase as u8, Ordering::Release);
    }
}

/// r: retention
fn log_loss_adjustment(r: f32) -> f32 {
    0.623 * (4. * r * (1. - r)).powf(0.738)
}

/// r: retention
///
/// c: review count
fn rmse_adjustment(r: f32, c: u32) -> f32 {
    0.0135 / (r.powf(0.504) - 1.14) + 0.176 / ((c as f32 / 1000.).powf(0.825) + 2.22) + 0.101
}

#[derive(Clone)]
struct TrainingItemsForFsrs {
    items: Vec<FSRSItem>,
    card_ids: Option<Vec<i64>>,
    revlog_ids: Option<Vec<RevlogId>>,
}

impl TrainingItemsForFsrs {
    fn with_card_and_revlog_ids(
        items: Vec<FSRSItem>,
        card_ids: Vec<i64>,
        revlog_ids: Vec<RevlogId>,
    ) -> Self {
        debug_assert_eq!(items.len(), card_ids.len());
        debug_assert_eq!(items.len(), revlog_ids.len());
        Self {
            items,
            card_ids: Some(card_ids),
            revlog_ids: Some(revlog_ids),
        }
    }

    fn without_card_ids(items: Vec<FSRSItem>) -> Self {
        Self {
            items,
            card_ids: None,
            revlog_ids: None,
        }
    }

    fn filter_non_same_day_evaluation_targets(self) -> Self {
        match (self.card_ids, self.revlog_ids) {
            (Some(card_ids), Some(revlog_ids)) => {
                let (items, ids): (Vec<_>, Vec<_>) = self
                    .items
                    .into_iter()
                    .zip_eq(card_ids)
                    .zip_eq(revlog_ids)
                    .filter_map(|((item, card_id), revlog_id)| {
                        has_long_term_target(&item).then_some((item, (card_id, revlog_id)))
                    })
                    .unzip();
                let (card_ids, revlog_ids) = ids.into_iter().unzip();
                Self {
                    items,
                    card_ids: Some(card_ids),
                    revlog_ids: Some(revlog_ids),
                }
            }
            (None, None) => Self::without_card_ids(
                self.items
                    .into_iter()
                    .filter(has_long_term_target)
                    .collect(),
            ),
            _ => unreachable!("card ids and revlog ids must be present together"),
        }
    }

    fn slice(&self, start: usize, end: usize) -> Self {
        Self {
            items: self.items[start..end].to_vec(),
            card_ids: self.card_ids.as_ref().map(|ids| ids[start..end].to_vec()),
            revlog_ids: self.revlog_ids.as_ref().map(|ids| ids[start..end].to_vec()),
        }
    }

    fn target_counts(&self) -> TrainingTargetCounts {
        training_target_counts_from_items(&self.items)
    }
}

#[derive(Clone)]
pub(crate) struct FsrsReviewPredictionContext {
    training: CompactTrainingItems,
    model_version: ComputeParametersVersion,
    num_relearning_steps: usize,
    enable_scheduling_penalties: bool,
}

impl FsrsReviewPredictionContext {
    pub(crate) fn from_revlogs(
        revlogs: Vec<RevlogEntry>,
        next_day_at: TimestampSecs,
        input: PrepareComputeParamsInput<'_>,
        progress: &mut ThrottlingProgressHandler<ComputeParamsProgress>,
    ) -> Result<Self> {
        let model_version =
            resolved_model_version(input.current_params, input.model_version_override);
        let include_same_day_reviews =
            include_same_day_training_entries(model_version, input.include_same_day_reviews);
        let training = compact_fsrs_items_for_training_with_progress(
            revlogs,
            next_day_at,
            input.ignore_revlogs_before,
            include_same_day_reviews,
            || progress.update(true, |_| {}),
        )?;
        progress.check_cancelled()?;
        Ok(Self {
            training,
            model_version,
            num_relearning_steps: input.num_of_relearning_steps,
            enable_scheduling_penalties: input.enable_scheduling_penalties,
        })
    }

    #[cfg(test)]
    pub(crate) fn from_prepared(prepared: &PreparedComputeParams) -> Self {
        Self {
            training: prepared.training.clone(),
            model_version: prepared.model_version,
            num_relearning_steps: prepared.num_of_relearning_steps,
            enable_scheduling_penalties: prepared.enable_scheduling_penalties,
        }
    }
}

fn has_long_term_target(item: &FSRSItem) -> bool {
    item.reviews
        .last()
        .is_some_and(|review| review.delta_t >= 1.0)
}

#[cfg(test)]
fn filter_non_same_day_evaluation_targets(items: Vec<FSRSItem>) -> Vec<FSRSItem> {
    TrainingItemsForFsrs::without_card_ids(items)
        .filter_non_same_day_evaluation_targets()
        .items
}

fn training_search<'a>(search: &'a str, search_for_training: Option<&'a str>) -> &'a str {
    match search_for_training.map(str::trim) {
        Some(non_empty) if !non_empty.is_empty() => non_empty,
        _ => search,
    }
}

fn uses_external_evaluation(training_search: &str, evaluation_search: &str) -> bool {
    training_search != evaluation_search
}

fn resolved_model_version(
    current_params: &[f32],
    model_version_override: Option<ComputeParametersVersion>,
) -> ComputeParametersVersion {
    model_version_override.unwrap_or_else(|| model_version_for_params(current_params))
}

fn training_target_counts_from_items(items: &[FSRSItem]) -> TrainingTargetCounts {
    let total_targets = items.len();
    let long_term_targets = items
        .iter()
        .filter(|item| has_long_term_target(item))
        .count();
    let short_term_targets = total_targets.saturating_sub(long_term_targets);
    TrainingTargetCounts {
        total_targets,
        long_term_targets,
        short_term_targets,
    }
}

struct HealthCheckTargets {
    count: u32,
    recalled: u32,
}

impl HealthCheckTargets {
    fn from_items(items: &[FSRSItem]) -> Self {
        Self {
            count: items.len() as u32,
            recalled: items.iter().fold(0, |passed, item| {
                passed + (item.reviews.last().map(|review| review.rating).unwrap_or(0) > 1) as u32
            }),
        }
    }

    fn passed(&self, eval: ModelEvaluation) -> bool {
        if self.count == 0 {
            return false;
        }
        let r = self.recalled as f32 / self.count as f32;
        let adjusted_log_loss = eval.log_loss / log_loss_adjustment(r);
        let adjusted_rmse = eval.rmse_bins / rmse_adjustment(r, self.count);
        adjusted_log_loss <= 1.11 || adjusted_rmse <= 1.53
    }
}

/// Below this many items, fsrs-rs returns its default or initial parameters
/// without training.
const MIN_ITEMS_FOR_TRAINING: usize = 64;

/// Keep the current parameters unless the optimized ones predict the training
/// items better. The log loss of two FSRS-7 parameter sets on the same items is
/// a fair comparison: scheduling penalties are part of the training objective
/// only. Older models use whole-day elapsed time and cannot be fairly compared
/// against FSRS-7 training items, so they are kept only when there is too
/// little data to train.
fn params_to_keep(
    current: &[f32],
    optimized: Params,
    items: &CompactTrainingItems,
) -> Result<Params> {
    let Ok(current_fsrs) = FSRS::new(current) else {
        return Ok(optimized);
    };
    if current_fsrs.version() != ModelVersion::Fsrs7 {
        return Ok(if items.len() < MIN_ITEMS_FOR_TRAINING {
            current.to_vec()
        } else {
            optimized
        });
    }

    let current_log_loss = current_fsrs
        .evaluate_from_card_prefixes(&items.prefixes, |_| true)?
        .log_loss;
    let optimized_log_loss = FSRS::new(&optimized)?
        .evaluate_from_card_prefixes(&items.prefixes, |_| true)?
        .log_loss;
    Ok(if current_log_loss <= optimized_log_loss {
        current.to_vec()
    } else {
        optimized
    })
}

fn time_series_split_items(
    sorted_items: TrainingItemsForFsrs,
    n_splits: usize,
) -> Vec<(TrainingItemsForFsrs, TrainingItemsForFsrs)> {
    time_series_split_ranges(sorted_items.items.len(), n_splits)
        .map(|test| {
            (
                sorted_items.slice(0, test.start),
                sorted_items.slice(test.start, test.end),
            )
        })
        .collect()
}

fn time_series_split_ranges(
    total_items: usize,
    n_splits: usize,
) -> impl Iterator<Item = std::ops::Range<usize>> {
    let segment_size = total_items / (n_splits + 1);
    (0..if segment_size == 0 { 0 } else { n_splits }).map(move |i| {
        let start = (i + 1) * segment_size;
        let end = if i == n_splits - 1 {
            total_items
        } else {
            (i + 2) * segment_size
        };
        start..end
    })
}

fn evaluate_with_time_series_splits_for_targets<F>(
    ComputeParametersInput {
        training_config,
        train_set,
        card_ids,
        enable_short_term,
        enable_sched_penalties,
        model_version,
        num_relearning_steps,
        ..
    }: ComputeParametersInput,
    include_target: impl Fn(&FSRSItem) -> bool,
    mut progress: F,
) -> Result<ModelEvaluation>
where
    F: FnMut(fsrs::ItemProgress) -> bool,
{
    if train_set.is_empty() {
        return Err(fsrs::FSRSError::NotEnoughData.into());
    }
    let splits = time_series_split_items(
        TrainingItemsForFsrs {
            items: train_set,
            card_ids,
            revlog_ids: None,
        },
        5,
    );
    if splits.is_empty() {
        return Err(fsrs::FSRSError::NotEnoughData.into());
    }
    let mut progress_info = fsrs::ItemProgress {
        current: 0,
        total: splits.len(),
    };
    let mut total_eval_items = 0usize;
    let mut weighted_log_loss = 0.0f64;
    let mut weighted_rmse_bins = 0.0f64;

    for (train_items, test_items) in splits {
        let parameters = compute_parameters(ComputeParametersInput {
            training_config,
            train_set: train_items.items,
            card_ids: train_items.card_ids,
            progress: None,
            enable_short_term,
            enable_sched_penalties,
            model_version,
            num_relearning_steps,
        })?;
        let eval_items = test_items
            .items
            .into_iter()
            .filter(|item| include_target(item))
            .collect_vec();
        if !eval_items.is_empty() {
            let fold_eval = FSRS::new(&parameters)?.evaluate(eval_items.clone(), |_| true)?;
            let fold_size = eval_items.len() as f64;
            weighted_log_loss += fold_eval.log_loss as f64 * fold_size;
            weighted_rmse_bins += fold_eval.rmse_bins as f64 * fold_size;
            total_eval_items += eval_items.len();
        }

        progress_info.current += 1;
        if !progress(progress_info) {
            return Err(fsrs::FSRSError::Interrupted.into());
        }
    }

    if total_eval_items == 0 {
        return Err(fsrs::FSRSError::NotEnoughData.into());
    }

    Ok(ModelEvaluation {
        log_loss: (weighted_log_loss / total_eval_items as f64) as f32,
        rmse_bins: (weighted_rmse_bins / total_eval_items as f64) as f32,
    })
}

fn evaluate_from_training_to_external_targets(
    ComputeParametersInput {
        training_config,
        train_set,
        card_ids,
        enable_short_term,
        enable_sched_penalties,
        model_version,
        num_relearning_steps,
        ..
    }: ComputeParametersInput,
    evaluation_set: Vec<FSRSItem>,
) -> Result<ModelEvaluation> {
    if train_set.is_empty() || evaluation_set.is_empty() {
        return Err(fsrs::FSRSError::NotEnoughData.into());
    }
    let parameters = compute_parameters(ComputeParametersInput {
        training_config,
        train_set,
        card_ids,
        progress: None,
        enable_short_term,
        enable_sched_penalties,
        model_version,
        num_relearning_steps,
    })?;
    Ok(FSRS::new(&parameters)?.evaluate(evaluation_set, |_| true)?)
}

pub(crate) fn compute_params_from_prepared(
    PreparedComputeParams {
        current_params,
        num_of_relearning_steps,
        model_version,
        include_same_day_reviews,
        enable_scheduling_penalties,
        dynamic_desired_retention_enabled,
        simulator_config,
        existing_card_input,
        training,
        ..
    }: PreparedComputeParams,
    progress: Option<Arc<Mutex<CombinedProgressState>>>,
    progress_phase: Option<SharedComputeParamsProgressPhase>,
    health_check: bool,
) -> Result<ComputeFsrsParamsResponse> {
    let fsrs_items = training.len() as u32;
    if fsrs_items == 0 {
        return Ok(ComputeFsrsParamsResponse {
            params: current_params,
            fsrs_items,
            health_check_passed: None,
            fsrs_dynamic_desired_retention_params: Vec::new(),
            fsrs_dynamic_desired_retention_weights: Vec::new(),
            fsrs_dynamic_desired_retention_avg_drs: Vec::new(),
            fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec::new(),
            fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec::new(),
            fsrs_dynamic_desired_retention_fixed_target_weights: Vec::new(),
            fsrs_dynamic_desired_retention_fixed_target_drs: Vec::new(),
            fsrs_dynamic_desired_retention_min: 0.0,
            fsrs_dynamic_desired_retention_max: 0.0,
        });
    }

    set_compute_params_progress_phase(
        progress_phase.as_ref(),
        ComputeParamsProgressPhase::OptimizingFsrsParams,
    );
    let input = ComputeParametersInput {
        training_config: None,
        train_set: Vec::new(),
        card_ids: None,
        progress: progress.clone(),
        enable_short_term: true,
        enable_sched_penalties: enable_scheduling_penalties,
        model_version,
        num_relearning_steps: Some(num_of_relearning_steps),
    };
    let params = coerce_computed_params_to_selected_version(
        model_version,
        &current_params,
        compute_compact_parameters(&training.prefixes, input)?,
    );
    let params = params_to_keep(&current_params, params, &training)?;
    let existing_cards = existing_card_input
        .map(|input| existing_cards_for_dynamic_desired_retention(input, &params))
        .transpose()?
        .unwrap_or_default();
    let dynamic_desired_retention = train_dynamic_desired_retention(
        dynamic_desired_retention_enabled,
        model_version,
        &simulator_config,
        &existing_cards,
        &params,
        progress,
        progress_phase.as_ref(),
    )?;

    let health_check_target_count = if include_same_day_reviews {
        training.len()
    } else {
        training.target_counts().long_term_targets
    };
    let health_check_passed = if health_check && health_check_target_count > 300 {
        let health_check_items = if include_same_day_reviews {
            training.expanded()
        } else {
            training.without_same_day_targets().expanded()
        };
        let health_check_targets = HealthCheckTargets::from_items(&health_check_items.items);
        evaluate_with_time_series_splits(
            ComputeParametersInput {
                training_config: None,
                train_set: health_check_items.items,
                card_ids: health_check_items.card_ids,
                progress: None,
                enable_short_term: true,
                enable_sched_penalties: enable_scheduling_penalties,
                model_version,
                num_relearning_steps: Some(num_of_relearning_steps),
            },
            |_| true,
        )
        .ok()
        .map(|eval| health_check_targets.passed(eval))
    } else {
        None
    };

    Ok(ComputeFsrsParamsResponse {
        params,
        fsrs_items,
        health_check_passed,
        fsrs_dynamic_desired_retention_params: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.params.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_weights: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.weights.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_avg_drs: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.avg_drs.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_fsrs_eq_weights: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.fsrs_eq_weights.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_fsrs_eq_drs: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.fsrs_eq_drs.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_fixed_target_weights: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.fixed_target_weights.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_fixed_target_drs: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.fixed_target_drs.clone())
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_min: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.retention_min)
            .unwrap_or_default(),
        fsrs_dynamic_desired_retention_max: dynamic_desired_retention
            .as_ref()
            .map(|calibration| calibration.retention_max)
            .unwrap_or_default(),
    })
}

fn train_dynamic_desired_retention(
    enabled: bool,
    model_version: ComputeParametersVersion,
    simulator_config: &SimulatorConfig,
    existing_cards: &[fsrs::Card],
    params: &[f32],
    progress: Option<Arc<Mutex<CombinedProgressState>>>,
    progress_phase: Option<&SharedComputeParamsProgressPhase>,
) -> Result<Option<DynamicDesiredRetentionCalibration>> {
    if !enabled || model_version != ComputeParametersVersion::Fsrs7 {
        return Ok(None);
    }

    let bounds = default_dynamic_desired_retention_bounds();
    set_compute_params_progress_phase(
        progress_phase,
        ComputeParamsProgressPhase::TrainingDynamicDesiredRetention,
    );
    let training_config = CostAdrTrainingConfig {
        retention_min: bounds.retention_min,
        retention_max: bounds.retention_max,
        progress,
        ..Default::default()
    };
    let result = if existing_cards.is_empty() {
        CostAdrPolicy::train_single_user(simulator_config, params, &training_config)?
    } else {
        CostAdrPolicy::train_single_user_with_existing_cards(
            simulator_config,
            params,
            &training_config,
            existing_cards,
        )?
    };
    let calibration_points = if existing_cards.is_empty() {
        result.policy.calibrate_average_desired_retention_range(
            simulator_config,
            params,
            DYNAMIC_DR_CALIBRATION_POINT_COUNT,
            training_config.simulation_seed,
        )?
    } else {
        result
            .policy
            .calibrate_average_desired_retention_range_with_existing_cards(
                simulator_config,
                params,
                DYNAMIC_DR_CALIBRATION_POINT_COUNT,
                training_config.simulation_seed,
                existing_cards,
            )?
    };
    let fsrs_equivalent_points = fsrs_equivalent_desired_retention_points(
        &training_config.baseline_desired_retentions,
        &result.baseline_metrics,
        &calibration_points,
    );
    let fixed_target_points = result
        .efficient_fixed_desired_retention_points(&training_config.baseline_desired_retentions);
    dynamic_desired_retention_calibration_from_parts(
        result.policy.coefficients,
        calibration_points
            .into_iter()
            .map(|point| (point.goal_cost_weight, point.average_desired_retention)),
        fsrs_equivalent_points,
        fixed_target_points
            .into_iter()
            .map(|point| (point.goal_cost_weight, point.desired_retention)),
        bounds,
    )
    .map(Some)
}

fn existing_cards_for_dynamic_desired_retention(
    input: ExistingCardInput,
    params: &[f32],
) -> Result<Vec<fsrs::Card>> {
    let fsrs = FSRS::new(legacy_fsrs_params(params))?;
    let mut items_by_card = fsrs_items_for_memory_states(
        &fsrs,
        params,
        input.revlogs,
        input.next_day_at,
        input.historical_retention,
        input.ignore_revlogs_before,
    )?
    .into_iter()
    .collect::<HashMap<_, _>>();
    let shared_params = Arc::new(params.to_vec());
    let converted_cards = input
        .cards
        .into_iter()
        .filter(is_included_card)
        .map(|mut card| -> Result<Option<fsrs::Card>> {
            let item = items_by_card.remove(&card.id).flatten();
            card.set_memory_state(&fsrs, params, item, input.historical_retention)?;
            let Some(memory_state) = card.memory_state else {
                return Ok(None);
            };
            card.desired_retention = Some(input.desired_retention);
            Ok(Card::convert_with_options(
                card,
                input.days_elapsed,
                memory_state,
                input.desired_retention,
                &shared_params,
            ))
        })
        .collect::<Result<Vec<_>>>()?
        .into_iter()
        .flatten()
        .collect();
    Ok(converted_cards)
}

const DYNAMIC_DR_CALIBRATION_POINT_COUNT: usize = 16;
const DYNAMIC_DR_DEFAULT_REVIEW_LIMIT: usize = 9999;
const DYNAMIC_DR_DEFAULT_MAX_COST_PERDAY_MINUTES: f32 = 720.0;

fn default_dynamic_desired_retention_bounds() -> DynamicDesiredRetentionBounds {
    DynamicDesiredRetentionBounds {
        retention_min: DEFAULT_RETENTION_MIN,
        retention_max: DEFAULT_RETENTION_MAX,
    }
}

fn shape_simulator_config_for_dynamic_desired_retention(
    config: &mut SimulatorConfig,
    revlogs: &[RevlogEntry],
    day_cutoff: i64,
    options: DynamicDesiredRetentionSimulatorOptions,
) -> Result<()> {
    let reviewed_cards = revlogs
        .iter()
        .map(|entry| entry.cid)
        .collect::<HashSet<_>>();
    if reviewed_cards.is_empty() {
        return Ok(());
    }
    let review_limit = options
        .review_limit
        .map(|value| value as usize)
        .unwrap_or(DYNAMIC_DR_DEFAULT_REVIEW_LIMIT);
    let max_cost_perday_minutes = options
        .max_cost_perday_minutes
        .unwrap_or(DYNAMIC_DR_DEFAULT_MAX_COST_PERDAY_MINUTES);
    require!(review_limit > 0, "Dynamic DR review limit must be positive");
    require!(
        max_cost_perday_minutes.is_finite() && max_cost_perday_minutes > 0.0,
        "Dynamic DR daily time budget must be positive minutes"
    );

    let active_new_card_days = revlogs
        .iter()
        .filter(|entry| entry.review_kind == RevlogReviewKind::Learning)
        .map(|entry| real_day(entry.id.0, day_cutoff))
        .collect::<HashSet<_>>()
        .len()
        .max(1);
    let learn_limit =
        ((reviewed_cards.len() as f32 / active_new_card_days as f32).round() as usize).max(1);

    config.deck_size = reviewed_cards.len();
    config.learn_span = active_new_card_days;
    config.learn_limit = learn_limit;
    config.review_limit = review_limit;
    config.max_cost_perday = max_cost_perday_minutes * 60.0;
    Ok(())
}

fn install_dynamic_desired_retention_review_time_cost_fn(
    config: &mut SimulatorConfig,
    revlogs: &[RevlogEntry],
    params: &[f32],
    next_day_at: TimestampSecs,
) -> Result<()> {
    let review_time_model = build_help_me_decide_review_time_model_from_revlogs(
        revlogs,
        params,
        next_day_at,
        false,
        config.state_rating_costs[1],
    )?;
    install_review_time_cost_fn(config, Arc::new(review_time_model));
    Ok(())
}

fn real_day(timestamp_millis: i64, day_cutoff: i64) -> i64 {
    (timestamp_millis / 1000 - day_cutoff) / 86400
}

fn dynamic_desired_retention_calibration_from_parts(
    params: Vec<f32>,
    points: impl IntoIterator<Item = (f32, Option<f32>)>,
    fsrs_equivalent_points: impl IntoIterator<Item = (f32, f32)>,
    fixed_target_points: impl IntoIterator<Item = (f32, f32)>,
    bounds: DynamicDesiredRetentionBounds,
) -> Result<DynamicDesiredRetentionCalibration> {
    let calibration = points
        .into_iter()
        .filter_map(|(weight, average_desired_retention)| {
            average_desired_retention.map(|avg_dr| (weight, avg_dr))
        })
        .collect::<Vec<_>>();
    require!(
        calibration.len() >= 2,
        "Dynamic DR calibration did not produce enough points"
    );

    let (weights, avg_drs) = calibration.into_iter().unzip();
    let (fsrs_eq_weights, fsrs_eq_drs) = fsrs_equivalent_points.into_iter().unzip();
    let (fixed_target_weights, fixed_target_drs) = fixed_target_points.into_iter().unzip();
    Ok(DynamicDesiredRetentionCalibration {
        params,
        weights,
        avg_drs,
        fsrs_eq_weights,
        fsrs_eq_drs,
        fixed_target_weights,
        fixed_target_drs,
        retention_min: bounds.retention_min,
        retention_max: bounds.retention_max,
    })
}

fn fsrs_equivalent_desired_retention_points(
    baseline_desired_retentions: &[f32],
    baseline_metrics: &[CostAdrMetrics],
    points: &[CostAdrEvaluationPoint],
) -> Vec<(f32, f32)> {
    let mut baseline_points = baseline_desired_retentions
        .iter()
        .copied()
        .zip(baseline_metrics.iter().copied())
        .filter(|(desired_retention, metrics)| {
            desired_retention.is_finite()
                && metrics.memorized_average.is_finite()
                && metrics.time_average.is_finite()
        })
        .map(|(desired_retention, metrics)| (metrics.memorized_average, desired_retention))
        .collect::<Vec<_>>();
    baseline_points.sort_by(|left, right| left.0.total_cmp(&right.0));

    points
        .iter()
        .filter_map(|point| {
            interpolated_desired_retention_for_memory_target(
                &baseline_points,
                point.metrics.memorized_average,
            )
            .map(|desired_retention| (point.goal_cost_weight, desired_retention))
        })
        .collect()
}

fn interpolated_desired_retention_for_memory_target(
    baseline_points: &[(f32, f32)],
    target_memorized_average: f32,
) -> Option<f32> {
    if !(target_memorized_average.is_finite() && baseline_points.len() >= 2) {
        return None;
    }

    baseline_points.windows(2).find_map(|pair| {
        let (left_memory, left_retention) = pair[0];
        let (right_memory, right_retention) = pair[1];
        if (left_memory - target_memorized_average) * (right_memory - target_memorized_average)
            > 0.0
        {
            return None;
        }
        if (left_memory - right_memory).abs() < f32::EPSILON {
            return Some(left_retention);
        }
        let t = ((target_memorized_average - left_memory) / (right_memory - left_memory))
            .clamp(0.0, 1.0);
        Some(left_retention + (right_retention - left_retention) * t)
    })
}

fn calibration_progress_total(total_validation_folds: usize) -> usize {
    if total_validation_folds == 0 {
        1
    } else {
        total_validation_folds * FSRS_CALIBRATION_PROGRESS_SCALE
    }
}

fn calibration_progress_current(
    completed_validation_folds: usize,
    total_validation_folds: usize,
    training_progress: &CombinedProgressState,
) -> usize {
    if total_validation_folds == 0 {
        return 0;
    }

    let completed = completed_validation_folds.min(total_validation_folds);
    let fold_current = training_progress.current();
    let fold_total = training_progress.total();
    let current_fold_progress = if completed == total_validation_folds || fold_total == 0 {
        0
    } else {
        (fold_current * FSRS_CALIBRATION_PROGRESS_SCALE / fold_total)
            .min(FSRS_CALIBRATION_PROGRESS_SCALE.saturating_sub(1))
    };
    completed * FSRS_CALIBRATION_PROGRESS_SCALE + current_fold_progress
}

impl Collection {
    /// Note this does not return an error if there are less than 400 items -
    /// the caller should instead check the fsrs_items count in the return
    /// value.
    pub fn compute_params(
        &mut self,
        request: ComputeParamsRequest,
    ) -> Result<ComputeFsrsParamsResponse> {
        let ComputeParamsRequest {
            search,
            ignore_revlogs_before_ms: ignore_revlogs_before,
            current_preset,
            total_presets,
            current_params,
            num_of_relearning_steps,
            health_check,
            include_same_day_reviews,
            enable_scheduling_penalties,
            model_version_override,
            dynamic_desired_retention_enabled,
            dynamic_desired_retention_review_limit,
            dynamic_desired_retention_max_cost_perday_minutes,
        } = request;

        self.clear_progress();
        let prepared = self.prepare_compute_params(PrepareComputeParamsInput {
            search,
            ignore_revlogs_before,
            current_params,
            num_of_relearning_steps,
            include_same_day_reviews,
            enable_scheduling_penalties,
            model_version_override,
            dynamic_desired_retention_enabled,
            historical_retention: 0.9,
            desired_retention: 0.9,
            dynamic_desired_retention_simulator_options: DynamicDesiredRetentionSimulatorOptions {
                review_limit: dynamic_desired_retention_review_limit,
                max_cost_perday_minutes: dynamic_desired_retention_max_cost_perday_minutes,
            },
        })?;

        if prepared.training.is_empty() {
            return Ok(ComputeFsrsParamsResponse {
                params: current_params.to_vec(),
                fsrs_items: 0,
                health_check_passed: None,
                fsrs_dynamic_desired_retention_params: Vec::new(),
                fsrs_dynamic_desired_retention_weights: Vec::new(),
                fsrs_dynamic_desired_retention_avg_drs: Vec::new(),
                fsrs_dynamic_desired_retention_fsrs_eq_weights: Vec::new(),
                fsrs_dynamic_desired_retention_fsrs_eq_drs: Vec::new(),
                fsrs_dynamic_desired_retention_fixed_target_weights: Vec::new(),
                fsrs_dynamic_desired_retention_fixed_target_drs: Vec::new(),
                fsrs_dynamic_desired_retention_min: 0.0,
                fsrs_dynamic_desired_retention_max: 0.0,
            });
        }
        // adapt the progress handler to our built-in progress handling

        let create_progress_thread = || -> Result<_> {
            let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
            anki_progress.update(false, |p| {
                p.current_preset = current_preset;
                p.total_presets = total_presets;
            })?;
            let progress = CombinedProgressState::new_shared();
            let progress_phase = new_compute_params_progress_phase();
            let progress2 = progress.clone();
            let progress_phase2 = progress_phase.clone();
            let progress_thread = thread::spawn(move || {
                let mut finished = false;
                while !finished {
                    thread::sleep(Duration::from_millis(100));
                    let mut guard = progress.lock().unwrap();
                    if let Err(_err) = anki_progress.update(false, |s| {
                        s.total_iterations = guard.total() as u32;
                        s.current_iteration = guard.current() as u32;
                        s.reviews = prepared.target_counts.total_targets as u32;
                        s.long_term_reviews = prepared.target_counts.long_term_targets as u32;
                        s.short_term_reviews = prepared.target_counts.short_term_targets as u32;
                        s.phase = ComputeParamsProgressPhase::from_shared(&progress_phase);
                        finished = guard.finished();
                    }) {
                        guard.want_abort = true;
                        return;
                    }
                }
            });
            Ok((progress2, progress_phase2, progress_thread))
        };

        let (progress, progress_phase, progress_thread) = create_progress_thread()?;
        let output = compute_params_from_prepared(
            prepared,
            Some(progress.clone()),
            Some(progress_phase),
            health_check,
        );
        progress_thread.join().ok();
        output
    }

    pub(crate) fn prepare_compute_params(
        &mut self,
        input: PrepareComputeParamsInput<'_>,
    ) -> Result<PreparedComputeParams> {
        let PrepareComputeParamsInput {
            search,
            ignore_revlogs_before,
            current_params,
            num_of_relearning_steps,
            include_same_day_reviews,
            enable_scheduling_penalties,
            model_version_override,
            dynamic_desired_retention_enabled,
            historical_retention,
            desired_retention,
            dynamic_desired_retention_simulator_options,
        } = input;
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        let simulator_config = if dynamic_desired_retention_enabled {
            let mut simulator_config = extract_simulator_config(
                revlogs.iter().cloned().map(Into::into).collect(),
                timing.next_day_at.into(),
                true,
            );
            shape_simulator_config_for_dynamic_desired_retention(
                &mut simulator_config,
                &revlogs,
                timing.next_day_at.into(),
                dynamic_desired_retention_simulator_options,
            )?;
            install_dynamic_desired_retention_review_time_cost_fn(
                &mut simulator_config,
                &revlogs,
                current_params,
                timing.next_day_at,
            )?;
            simulator_config
        } else {
            SimulatorConfig::default()
        };
        let model_version = resolved_model_version(current_params, model_version_override);
        let include_same_day_reviews =
            include_same_day_training_entries(model_version, include_same_day_reviews);
        let existing_card_input = if dynamic_desired_retention_enabled
            && model_version == ComputeParametersVersion::Fsrs7
        {
            let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
            let cards = guard.col.storage.all_searched_cards()?;
            drop(guard);
            Some(ExistingCardInput {
                cards,
                revlogs: revlogs.clone(),
                next_day_at: timing.next_day_at,
                days_elapsed: timing.days_elapsed as i32,
                ignore_revlogs_before,
                historical_retention,
                desired_retention,
            })
        } else {
            None
        };
        let training = compact_fsrs_items_for_training(
            revlogs,
            timing.next_day_at,
            ignore_revlogs_before,
            include_same_day_reviews,
        )?;
        let target_counts = training.target_counts();
        Ok(PreparedComputeParams {
            current_params: current_params.to_vec(),
            num_of_relearning_steps,
            model_version,
            include_same_day_reviews,
            enable_scheduling_penalties,
            dynamic_desired_retention_enabled,
            simulator_config,
            existing_card_input,
            training,
            target_counts,
        })
    }

    pub(crate) fn revlog_for_srs(
        &mut self,
        search: impl TryIntoSearch,
    ) -> Result<Vec<RevlogEntry>> {
        let search = search.try_into_search()?;
        // a whole-collection search can match revlog entries of deleted cards, too
        if let Node::Group(nodes) = &search {
            if let &[Node::Search(SearchNode::WholeCollection)] = &nodes[..] {
                return self.storage.get_all_revlog_entries_in_card_order();
            }
        }
        self.search_cards_into_table(search, SortMode::NoOrder)?
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()
    }

    /// Used for exporting revlogs for algorithm research.
    pub fn export_dataset(&mut self, min_entries: usize, target_path: &Path) -> Result<()> {
        let revlog_entries = self.storage.get_revlog_entries_for_export_dataset()?;
        if revlog_entries.len() < min_entries {
            return Err(AnkiError::FsrsInsufficientData);
        }
        let revlogs = revlog_entries
            .into_iter()
            .map(revlog_entry_to_proto)
            .collect_vec();
        let cards = self.storage.get_all_card_entries()?;

        let decks_map = self.storage.get_decks_map()?;
        let deck_name_to_id: HashMap<String, DeckId> = decks_map
            .into_iter()
            .map(|(id, deck)| (deck.name.to_string(), id))
            .collect();

        let decks = self
            .storage
            .get_all_decks()?
            .into_iter()
            .filter_map(|deck| {
                if let Some(preset_id) = deck.config_id().map(|id| id.0) {
                    let parent_id = immediate_parent_name(&deck.name.to_string())
                        .and_then(|parent_name| deck_name_to_id.get(parent_name))
                        .map(|id| id.0)
                        .unwrap_or(0);
                    Some(DeckEntry {
                        id: deck.id.0,
                        parent_id,
                        preset_id,
                    })
                } else {
                    None
                }
            })
            .collect_vec();
        let next_day_at = self.timing_today()?.next_day_at.0;
        let dataset = Dataset {
            revlogs,
            cards,
            decks,
            next_day_at,
        };
        let data = dataset.encode_to_vec();
        write_file(target_path, data)?;
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn evaluate_params(
        &mut self,
        search: &str,
        search_for_training: Option<&str>,
        ignore_revlogs_before: TimestampMillis,
        num_of_relearning_steps: usize,
        model_version: ComputeParametersVersion,
        include_same_day_reviews: Option<bool>,
        include_same_day_reviews_for_training: Option<bool>,
        enable_scheduling_penalties: bool,
    ) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let training_search = training_search(search, search_for_training);
        let training_revlogs = self.revlog_for_srs(training_search)?;
        let evaluation_revlogs = if training_search == search {
            training_revlogs.clone()
        } else {
            self.revlog_for_srs(search)?
        };
        let include_same_day_reviews_for_training =
            include_same_day_training_entries(model_version, include_same_day_reviews_for_training);
        let include_same_day_reviews =
            include_same_day_training_entries(model_version, include_same_day_reviews);
        let training_items = fsrs_items_for_training(
            training_revlogs,
            timing.next_day_at,
            ignore_revlogs_before,
            include_same_day_reviews_for_training,
        );
        let evaluation_base_items = fsrs_items_for_training(
            evaluation_revlogs,
            timing.next_day_at,
            ignore_revlogs_before,
            include_same_day_reviews,
        );
        let evaluation_items = if include_same_day_reviews {
            evaluation_base_items
        } else {
            evaluation_base_items.filter_non_same_day_evaluation_targets()
        };
        let target_counts = evaluation_items.target_counts();
        let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
        anki_progress.state.reviews = target_counts.total_targets as u32;
        anki_progress.state.long_term_reviews = target_counts.long_term_targets as u32;
        anki_progress.state.short_term_reviews = target_counts.short_term_targets as u32;
        // Ensure UI receives review counts even in paths that don't emit per-fold
        // progress.
        let _ = anki_progress.update(false, |_| {});

        let eval = if uses_external_evaluation(training_search, search) {
            evaluate_from_training_to_external_targets(
                ComputeParametersInput {
                    training_config: None,
                    train_set: training_items.items,
                    card_ids: training_items.card_ids,
                    progress: None,
                    enable_short_term: true,
                    enable_sched_penalties: enable_scheduling_penalties,
                    model_version,
                    num_relearning_steps: Some(num_of_relearning_steps),
                },
                evaluation_items.items,
            )?
        } else if include_same_day_reviews == include_same_day_reviews_for_training {
            evaluate_with_time_series_splits(
                ComputeParametersInput {
                    training_config: None,
                    train_set: evaluation_items.items,
                    card_ids: evaluation_items.card_ids,
                    progress: None,
                    enable_short_term: true,
                    enable_sched_penalties: enable_scheduling_penalties,
                    model_version,
                    num_relearning_steps: Some(num_of_relearning_steps),
                },
                |ip| {
                    anki_progress
                        .update(false, |p| {
                            p.total_iterations = ip.total as u32;
                            p.current_iteration = ip.current as u32;
                        })
                        .is_ok()
                },
            )?
        } else {
            evaluate_with_time_series_splits_for_targets(
                ComputeParametersInput {
                    training_config: None,
                    train_set: training_items.items,
                    card_ids: training_items.card_ids,
                    progress: None,
                    enable_short_term: true,
                    enable_sched_penalties: enable_scheduling_penalties,
                    model_version,
                    num_relearning_steps: Some(num_of_relearning_steps),
                },
                |item| {
                    include_same_day_reviews
                        || item
                            .reviews
                            .last()
                            .is_some_and(|review| review.delta_t >= 1.0)
                },
                |ip| {
                    anki_progress
                        .update(false, |p| {
                            p.total_iterations = ip.total as u32;
                            p.current_iteration = ip.current as u32;
                        })
                        .is_ok()
                },
            )?
        };
        Ok(eval)
    }

    pub fn evaluate_params_legacy(
        &mut self,
        params: &Params,
        search: &str,
        ignore_revlogs_before: TimestampMillis,
        include_same_day_reviews: Option<bool>,
    ) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let revlogs: Vec<RevlogEntry> = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let model_version = model_version_for_params(params);
        let include_same_day_reviews =
            include_same_day_training_entries(model_version, include_same_day_reviews);
        let items = compact_fsrs_items_for_training(
            revlogs,
            timing.next_day_at,
            ignore_revlogs_before,
            include_same_day_reviews,
        )?;
        let items = if include_same_day_reviews {
            items
        } else {
            items.without_same_day_targets()
        };
        let target_counts = items.target_counts();
        anki_progress.state.reviews = target_counts.total_targets as u32;
        anki_progress.state.long_term_reviews = target_counts.long_term_targets as u32;
        anki_progress.state.short_term_reviews = target_counts.short_term_targets as u32;
        let fsrs = FSRS::new(legacy_fsrs_params(params))?;
        Ok(fsrs.evaluate_from_card_prefixes(&items.prefixes, |ip| {
            anki_progress
                .update(false, |p| {
                    p.total_iterations = ip.total as u32;
                    p.current_iteration = ip.current as u32;
                })
                .is_ok()
        })?)
    }
}

fn coerce_computed_params_to_selected_version(
    model_version: ComputeParametersVersion,
    current_params: &[f32],
    computed_params: Vec<f32>,
) -> Vec<f32> {
    if current_params.is_empty() || current_params.len() == computed_params.len() {
        return computed_params;
    }

    let expected_len = match model_version {
        ComputeParametersVersion::Fsrs7 => 34,
        ComputeParametersVersion::Fsrs6 => 21,
    };

    if computed_params.len() == expected_len {
        return computed_params;
    }

    if current_params.len() == expected_len {
        current_params.to_vec()
    } else {
        computed_params
    }
}

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeParamsProgress {
    pub current_iteration: u32,
    pub total_iterations: u32,
    /// Total training targets used by optimizer (long-term + same-day)
    pub reviews: u32,
    /// Targets where delta_t >= 1 day
    pub long_term_reviews: u32,
    /// Targets where delta_t < 1 day
    pub short_term_reviews: u32,
    /// Only used in 'compute all params' case
    pub current_preset: u32,
    /// Only used in 'compute all params' case
    pub total_presets: u32,
    pub phase: ComputeParamsProgressPhase,
}

#[derive(Default, Clone, Debug)]
pub struct ComputeAllParamsProgress {
    pub current_iteration: u32,
    pub total_iterations: u32,
    pub presets: Vec<ComputeAllParamsPresetProgress>,
}

#[derive(Default, Clone, Debug)]
pub struct ComputeAllParamsPresetProgress {
    pub name: String,
    pub current_iteration: u32,
    pub total_iterations: u32,
    pub reviews: u32,
    pub long_term_reviews: u32,
    pub short_term_reviews: u32,
    pub finished: bool,
    pub skipped: bool,
    pub phase: ComputeParamsProgressPhase,
}

#[derive(Default, Clone, Copy, Debug)]
pub(crate) struct TrainingTargetCounts {
    pub total_targets: usize,
    pub long_term_targets: usize,
    pub short_term_targets: usize,
}

/// Convert a series of revlog entries sorted by card id into FSRS items.
fn fsrs_items_for_training(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    review_revlogs_before: TimestampMillis,
    include_same_day: bool,
) -> TrainingItemsForFsrs {
    fsrs_items_for_training_with_progress(
        revlogs,
        next_day_at,
        review_revlogs_before,
        include_same_day,
        || Ok(()),
    )
    .expect("preparation without a progress handler cannot be interrupted")
}

fn fsrs_items_for_training_with_progress(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    review_revlogs_before: TimestampMillis,
    include_same_day: bool,
    mut check_cancelled: impl FnMut() -> Result<()>,
) -> Result<TrainingItemsForFsrs> {
    let mut revlogs_out = Vec::new();
    for (cid, entries) in &revlogs.into_iter().chunk_by(|r| r.cid) {
        check_cancelled()?;
        if let Some(reviews) = reviews_for_fsrs_with_progress(
            entries.collect(),
            next_day_at,
            true,
            review_revlogs_before,
            include_same_day,
            &mut check_cancelled,
        )? {
            revlogs_out.extend(
                reviews
                    .fsrs_items
                    .into_iter()
                    .map(|(revlog_id, item)| (revlog_id, cid, item)),
            );
        }
    }
    // Sort by RevlogId
    revlogs_out.sort_by_key(|(revlog_id, _, _)| revlog_id.0);
    let (revlog_ids, card_items): (Vec<_>, Vec<_>) = revlogs_out
        .into_iter()
        .map(|(revlog_id, card_id, item)| (revlog_id, (card_id.0, item)))
        .unzip();
    let (card_ids, items) = card_items.into_iter().unzip();
    Ok(TrainingItemsForFsrs::with_card_and_revlog_ids(
        items, card_ids, revlog_ids,
    ))
}

pub(crate) fn compute_fsrs_review_retrievability_calibration_rows(
    params: &[f32],
    context: &FsrsReviewPredictionContext,
    include_validation_folds: bool,
    anki_progress: ThrottlingProgressHandler<ComputeParamsProgress>,
) -> Result<(
    Vec<FsrsReviewRetrievabilityCacheRow>,
    ThrottlingProgressHandler<ComputeParamsProgress>,
)> {
    let (progress, done, progress_thread) = create_fsrs_review_retrievability_progress_thread(
        anki_progress,
        context,
        include_validation_folds,
    )?;
    let rows = fsrs_review_retrievability_cache_rows(
        params,
        context,
        include_validation_folds,
        Some(&progress),
    );
    done.store(true, Ordering::Release);
    let anki_progress = progress_thread
        .join()
        .expect("calibration progress thread panicked");
    if fsrs_review_retrievability_progress_wants_abort(Some(&progress)) {
        return Err(AnkiError::Interrupted);
    }
    Ok((rows?, anki_progress))
}

fn create_fsrs_review_retrievability_progress_thread(
    mut anki_progress: ThrottlingProgressHandler<ComputeParamsProgress>,
    context: &FsrsReviewPredictionContext,
    include_validation_folds: bool,
) -> Result<(
    FsrsReviewRetrievabilityProgress,
    Arc<AtomicBool>,
    thread::JoinHandle<ThrottlingProgressHandler<ComputeParamsProgress>>,
)> {
    let total_validation_folds = if include_validation_folds {
        FSRS_VALIDATION_FOLDS
    } else {
        0
    };
    let target_counts = context.training.target_counts();
    let total_iterations = calibration_progress_total(total_validation_folds);
    anki_progress.update(false, |p| {
        p.current_iteration = 0;
        p.total_iterations = total_iterations as u32;
        p.reviews = target_counts.total_targets as u32;
        p.long_term_reviews = target_counts.long_term_targets as u32;
        p.short_term_reviews = target_counts.short_term_targets as u32;
        p.current_preset = 1;
        p.total_presets = 1;
    })?;

    let progress = FsrsReviewRetrievabilityProgress {
        training_progress: CombinedProgressState::new_shared(),
        completed_validation_folds: Arc::new(AtomicUsize::new(0)),
    };
    let done = Arc::new(AtomicBool::new(false));
    let training_progress = progress.training_progress.clone();
    let completed_validation_folds = progress.completed_validation_folds.clone();
    let done_for_thread = done.clone();
    let progress_thread = thread::spawn(move || {
        while !done_for_thread.load(Ordering::Acquire) {
            thread::sleep(Duration::from_millis(100));
            let mut guard = training_progress.lock().unwrap();
            let current_iterations = calibration_progress_current(
                completed_validation_folds.load(Ordering::Acquire),
                total_validation_folds,
                &guard,
            );
            if anki_progress
                .update(false, |s| {
                    s.current_iteration = current_iterations as u32;
                    s.total_iterations = total_iterations as u32;
                })
                .is_err()
            {
                guard.want_abort = true;
                return anki_progress;
            }
        }
        anki_progress
    });
    Ok((progress, done, progress_thread))
}

pub(crate) fn fsrs_review_retrievability_cache_rows(
    params: &[f32],
    context: &FsrsReviewPredictionContext,
    include_validation_folds: bool,
    progress: Option<&FsrsReviewRetrievabilityProgress>,
) -> Result<Vec<FsrsReviewRetrievabilityCacheRow>> {
    let mut rows = fsrs_review_retrievability_predictions(
        params,
        &context.training,
        0..context.training.len(),
        progress,
    )?
    .into_iter()
    .map(|(revlog_id, prediction)| FsrsReviewRetrievabilityCacheRow {
        revlog_id,
        prediction,
        sample_role: FsrsReviewRetrievabilitySampleRole::FinalFit,
        fold_index: -1,
    })
    .collect_vec();
    if fsrs_review_retrievability_progress_wants_abort(progress) {
        return Err(AnkiError::Interrupted);
    }
    if include_validation_folds {
        match fsrs_validation_retrievability_cache_rows(context, progress) {
            Ok(validation_rows) => rows.extend(validation_rows),
            Err(AnkiError::Interrupted) => return Err(AnkiError::Interrupted),
            Err(err) => tracing::debug!(?err, "failed to compute FSRS validation cache rows"),
        }
    }
    Ok(rows)
}

fn fsrs_review_retrievability_progress_wants_abort(
    progress: Option<&FsrsReviewRetrievabilityProgress>,
) -> bool {
    progress.is_some_and(|progress| progress.training_progress.lock().unwrap().want_abort)
}

fn fsrs_validation_retrievability_cache_rows(
    context: &FsrsReviewPredictionContext,
    progress: Option<&FsrsReviewRetrievabilityProgress>,
) -> Result<Vec<FsrsReviewRetrievabilityCacheRow>> {
    if context.training.is_empty() {
        return Ok(Vec::new());
    }
    let mut rows = Vec::new();
    let mut fold_prefixes = CardPrefixes {
        cards: context.training.prefixes.cards.clone(),
        prefixes: Vec::new(),
    };
    for (fold_index, test) in
        time_series_split_ranges(context.training.len(), FSRS_VALIDATION_FOLDS).enumerate()
    {
        if let Some(progress) = progress {
            progress
                .completed_validation_folds
                .store(fold_index, Ordering::Release);
        }
        if fsrs_review_retrievability_progress_wants_abort(progress) {
            return Err(AnkiError::Interrupted);
        }
        // Reuse the card histories while selecting one chronological training fold.
        fold_prefixes.prefixes.clear();
        for chunk in context.training.prefixes.prefixes[..test.start].chunks(1024) {
            if fsrs_review_retrievability_progress_wants_abort(progress) {
                return Err(AnkiError::Interrupted);
            }
            fold_prefixes.prefixes.extend_from_slice(chunk);
        }
        let parameters = match compute_compact_parameters(
            &fold_prefixes,
            ComputeParametersInput {
                training_config: None,
                train_set: Vec::new(),
                card_ids: None,
                progress: progress.map(|progress| progress.training_progress.clone()),
                enable_short_term: true,
                enable_sched_penalties: context.enable_scheduling_penalties,
                model_version: context.model_version,
                num_relearning_steps: Some(context.num_relearning_steps),
            },
        ) {
            Ok(parameters) => parameters,
            Err(AnkiError::Interrupted) => return Err(AnkiError::Interrupted),
            Err(err) => {
                tracing::debug!(?err, fold_index, "skipping FSRS validation cache fold");
                continue;
            }
        };
        let predictions = match fsrs_review_retrievability_predictions(
            &parameters,
            &context.training,
            test,
            progress,
        ) {
            Ok(predictions) => predictions,
            Err(AnkiError::Interrupted) => return Err(AnkiError::Interrupted),
            Err(err) => {
                tracing::debug!(
                    ?err,
                    fold_index,
                    "skipping FSRS validation cache fold predictions"
                );
                continue;
            }
        };
        rows.extend(predictions.into_iter().map(|(revlog_id, prediction)| {
            FsrsReviewRetrievabilityCacheRow {
                revlog_id,
                prediction,
                sample_role: FsrsReviewRetrievabilitySampleRole::ValidationFold,
                fold_index: fold_index as i32,
            }
        }));
        if let Some(progress) = progress {
            progress
                .completed_validation_folds
                .store(fold_index + 1, Ordering::Release);
        }
    }
    Ok(rows)
}

pub(crate) struct ReviewsForFsrs {
    /// The revlog entries that remain after filtering (e.g. excluding
    /// review entries prior to a card being reset).
    pub filtered_revlogs: Vec<RevlogEntry>,
    /// FSRS items derived from the filtered revlogs.
    pub fsrs_items: Vec<(RevlogId, FSRSItem)>,
    /// True if there is enough history to derive memory state from history
    /// alone. If false, memory state will be derived from SM2.
    pub revlogs_complete: bool,
}

struct FilteredReviewsForFsrs {
    entries: Vec<RevlogEntry>,
    delta_ts: Vec<f32>,
    revlogs_complete: bool,
}

/// Filter out unwanted revlog entries, then create a series of FSRS items for
/// training/memory state calculation.
///
/// Filtering consists of removing revlog entries before the supplied timestamp,
/// and removing items such as reviews that happened prior to a card being reset
/// to new.
pub(crate) fn reviews_for_fsrs(
    entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
    ignore_revlogs_before: TimestampMillis,
    include_same_day_training_entries: bool,
) -> Option<ReviewsForFsrs> {
    reviews_for_fsrs_with_progress(
        entries,
        next_day_at,
        training,
        ignore_revlogs_before,
        include_same_day_training_entries,
        &mut || Ok(()),
    )
    .expect("preparation without a progress handler cannot be interrupted")
}

fn filtered_reviews_for_fsrs_with_progress(
    mut entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
    ignore_revlogs_before: TimestampMillis,
    include_same_day_training_entries: bool,
    check_cancelled: &mut impl FnMut() -> Result<()>,
) -> Result<Option<FilteredReviewsForFsrs>> {
    let mut first_of_last_learn_entries = None;
    let mut first_user_grade_idx = None;
    let mut revlogs_complete = false;
    // Working backwards from the latest review...
    for (index, entry) in entries.iter().enumerate().rev() {
        if index % 128 == 0 {
            check_cancelled()?;
        }
        if entry.is_cramming() {
            continue;
        }
        // For incomplete review histories, initial memory state is based on the first
        // user-graded review after the cutoff date with interval >= 1d.
        let within_cutoff = entry.id.0 > ignore_revlogs_before.0;
        let user_graded = entry.has_rating();
        let interday = entry.interval >= 1 || entry.interval <= -86400;
        if user_graded && within_cutoff && interday {
            first_user_grade_idx = Some(index);
        }

        if user_graded && entry.review_kind == RevlogReviewKind::Learning {
            first_of_last_learn_entries = Some(index);
            revlogs_complete = true;
        } else if entry.is_reset() {
            // Ignore entries prior to a `Reset` if a learning step has come after,
            // but consider revlogs complete.
            if first_of_last_learn_entries.is_some() {
                revlogs_complete = true;
                break;
            // Ignore entries prior to a `Reset` if the user has graded a card
            // after the reset.
            } else if first_user_grade_idx.is_some() {
                revlogs_complete = false;
                break;
            // User has not graded the card since it was reset, so all history
            // filtered out.
            } else {
                return Ok(None);
            }
        // Previous versions of Anki didn't add a revlog entry when the card was
        // reset.
        } else if first_of_last_learn_entries.is_some() {
            break;
        }
    }
    if training {
        // While training, ignore the entire card if the first learning step of the last
        // group of learning steps is before the ignore_revlogs_before date
        if let Some(idx) = first_of_last_learn_entries {
            if entries[idx].id.0 < ignore_revlogs_before.0 {
                return Ok(None);
            }
        }
    } else {
        // While reviewing, if the first learning step is before the ignore date,
        // we ignore it, and will fall back on SM2 info and the last user grade below.
        if let Some(idx) = first_of_last_learn_entries {
            if entries[idx].id.0 < ignore_revlogs_before.0 && idx < entries.len() - 1 {
                revlogs_complete = false;
                first_of_last_learn_entries = None;
            }
        }
    }
    if let Some(idx) = first_of_last_learn_entries {
        // start from the learning step
        if idx > 0 {
            entries.drain(..idx);
        }
    } else if training {
        // when training, we ignore cards that don't have any learning steps
        return Ok(None);
    } else {
        // if no valid user grades were found, ignore the card.
        let Some(idx) = first_user_grade_idx else {
            return Ok(None);
        };
        // if there are no learning entries, but the user has reviewed the card,
        // we ignore all entries before the first grade
        if idx > 0 {
            entries.drain(..idx);
        }
    }

    // Filter out unwanted entries
    entries.retain(|entry| entry.has_rating_and_affects_scheduling());

    let delta_ts = fsrs_review_delta_ts(&entries, next_day_at, include_same_day_training_entries);

    Ok(Some(FilteredReviewsForFsrs {
        entries,
        delta_ts,
        revlogs_complete,
    }))
}

fn reviews_for_fsrs_with_progress(
    entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
    ignore_revlogs_before: TimestampMillis,
    include_same_day_training_entries: bool,
    check_cancelled: &mut impl FnMut() -> Result<()>,
) -> Result<Option<ReviewsForFsrs>> {
    let Some(FilteredReviewsForFsrs {
        entries,
        delta_ts,
        revlogs_complete,
    }) = filtered_reviews_for_fsrs_with_progress(
        entries,
        next_day_at,
        training,
        ignore_revlogs_before,
        include_same_day_training_entries,
        check_cancelled,
    )?
    else {
        return Ok(None);
    };

    let items = if training {
        // Convert the remaining entries into separate FSRSItems, where each item
        // contains all reviews done until then.
        let mut items = Vec::with_capacity(entries.len());
        let mut current_reviews = Vec::with_capacity(entries.len());
        for (idx, (entry, &delta_t)) in entries.iter().zip(delta_ts.iter()).enumerate() {
            if idx % 128 == 0 {
                check_cancelled()?;
            }
            current_reviews.push(FSRSReview {
                rating: entry.button_chosen as u32,
                delta_t,
            });
            let keep_for_training = delta_t > 0.0 || include_same_day_training_entries;
            if idx >= 1 && keep_for_training {
                items.push((
                    entry.id,
                    FSRSItem {
                        reviews: current_reviews.clone(),
                    },
                ));
            }
        }
        items
    } else {
        // When not training, we only need the final FSRS item, which represents
        // the complete history of the card. This avoids expensive clones in a loop.
        let reviews = entries
            .iter()
            .zip(delta_ts.iter())
            .map(|(entry, &delta_t)| FSRSReview {
                rating: entry.button_chosen as u32,
                delta_t,
            })
            .collect();
        let last_entry = entries.last().unwrap();

        vec![(last_entry.id, FSRSItem { reviews })]
    };

    if items.is_empty() {
        Ok(None)
    } else {
        Ok(Some(ReviewsForFsrs {
            fsrs_items: items,
            revlogs_complete,
            filtered_revlogs: entries,
        }))
    }
}

fn fsrs_review_delta_ts(
    entries: &[RevlogEntry],
    next_day_at: TimestampSecs,
    include_same_day_training_entries: bool,
) -> Vec<f32> {
    iter::once(0.0f32)
        .chain(entries.iter().tuple_windows().map(|(previous, current)| {
            let elapsed_days =
                previous.days_elapsed(next_day_at) - current.days_elapsed(next_day_at);
            if include_same_day_training_entries {
                // FSRS-7 accepts fractional elapsed days; use revlog timestamps directly.
                let elapsed_millis = current.id.0.saturating_sub(previous.id.0).max(1) as f32;
                elapsed_millis / 86_400_000.0
            } else {
                elapsed_days as f32
            }
        }))
        .collect_vec()
}

impl RevlogEntry {
    fn days_elapsed(&self, next_day_at: TimestampSecs) -> u32 {
        (next_day_at.elapsed_secs_since(self.id.as_secs()) / 86_400).max(0) as u32
    }
}

fn revlog_entry_to_proto(e: RevlogEntry) -> anki_proto::stats::RevlogEntry {
    anki_proto::stats::RevlogEntry {
        id: e.id.0,
        cid: e.cid.0,
        usn: 0,
        button_chosen: e.button_chosen as u32,
        interval: e.interval,
        last_interval: e.last_interval,
        ease_factor: e.ease_factor,
        taken_millis: e.taken_millis,
        review_kind: match e.review_kind {
            RevlogReviewKind::Learning => revlog_entry::ReviewKind::Learning,
            RevlogReviewKind::Review => revlog_entry::ReviewKind::Review,
            RevlogReviewKind::Relearning => revlog_entry::ReviewKind::Relearning,
            RevlogReviewKind::Filtered => revlog_entry::ReviewKind::Filtered,
            RevlogReviewKind::Manual => revlog_entry::ReviewKind::Manual,
            RevlogReviewKind::Rescheduled => revlog_entry::ReviewKind::Rescheduled,
        } as i32,
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use std::path::Path;
    use std::path::PathBuf;
    use std::time::Instant;

    use fsrs::CardPrefix;
    use fsrs::CardReviews;
    use rusqlite::params;
    use tempfile::tempdir;
    use tempfile::TempDir;

    use super::*;
    use crate::backend::Backend;
    use crate::collection::Collection;
    use crate::collection::CollectionBuilder;
    use crate::services::BackendCollectionService;
    use crate::services::BackendSchedulerService;

    const FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE_FOR_BENCH: &str =
        "search_stats_fsrs_review_retrievability";

    const NEXT_DAY_AT: TimestampSecs = TimestampSecs(86400 * 1000);

    fn days_ago_ms(days_ago: i64) -> TimestampMillis {
        ((NEXT_DAY_AT.0 - days_ago * 86400) * 1000).into()
    }

    pub(crate) fn revlog(review_kind: RevlogReviewKind, days_ago: i64) -> RevlogEntry {
        let button_chosen = match review_kind {
            RevlogReviewKind::Manual | RevlogReviewKind::Rescheduled => 0,
            _ => 3,
        };
        RevlogEntry {
            review_kind,
            id: days_ago_ms(days_ago).into(),
            button_chosen,
            interval: 1,
            ..Default::default()
        }
    }

    fn revlog_for_card(cid: i64, review_kind: RevlogReviewKind, days_ago: i64) -> RevlogEntry {
        RevlogEntry {
            cid: CardId(cid),
            ..revlog(review_kind, days_ago)
        }
    }

    #[test]
    fn calibration_preparation_can_be_cancelled_within_one_long_card() {
        let entries = std::iter::once(revlog_for_card(1, RevlogReviewKind::Learning, 300))
            .chain(
                (1..300)
                    .rev()
                    .map(|day| revlog_for_card(1, RevlogReviewKind::Review, day)),
            )
            .collect();
        let mut checks = 0;
        let result = compact_fsrs_items_for_training_with_progress(
            entries,
            NEXT_DAY_AT,
            0.into(),
            true,
            || {
                checks += 1;
                if checks == 3 {
                    Err(AnkiError::Interrupted)
                } else {
                    Ok(())
                }
            },
        );
        assert!(matches!(result, Err(AnkiError::Interrupted)));
        assert_eq!(checks, 3);
    }

    #[test]
    fn calibration_predictions_honor_cancellation_without_validation_training() {
        let items = compact_test_items(vec![FSRSItem {
            reviews: vec![review(0), review(1)],
        }]);
        let progress = FsrsReviewRetrievabilityProgress {
            training_progress: Arc::new(Mutex::new(CombinedProgressState::default())),
            completed_validation_folds: Arc::new(AtomicUsize::new(0)),
        };
        progress.training_progress.lock().unwrap().want_abort = true;
        let result = fsrs_review_retrievability_predictions(
            &fsrs::DEFAULT_PARAMETERS,
            &items,
            0..items.len(),
            Some(&progress),
        );
        assert!(matches!(result, Err(AnkiError::Interrupted)));
    }

    #[test]
    fn compact_training_preserves_items_parameters_and_evaluation() -> Result<()> {
        let mut revlogs = Vec::new();
        for card in 1..=30 {
            for days_ago in (1..=6).rev() {
                let kind = if days_ago == 6 {
                    RevlogReviewKind::Learning
                } else {
                    RevlogReviewKind::Review
                };
                let mut entry = revlog_for_card(card, kind, days_ago);
                entry.id.0 += card * 100;
                entry.button_chosen = if (card + days_ago) % 5 == 0 { 1 } else { 3 };
                revlogs.push(entry);
            }
        }
        for version in [
            ComputeParametersVersion::Fsrs6,
            ComputeParametersVersion::Fsrs7,
        ] {
            let include_same_day = version == ComputeParametersVersion::Fsrs7;
            let expanded =
                fsrs_items_for_training(revlogs.clone(), NEXT_DAY_AT, 0.into(), include_same_day);
            let compact = compact_fsrs_items_for_training(
                revlogs.clone(),
                NEXT_DAY_AT,
                0.into(),
                include_same_day,
            )?;
            let roundtrip = compact.expanded();
            assert_eq!(roundtrip.items, expanded.items);
            assert_eq!(roundtrip.card_ids, expanded.card_ids);
            assert_eq!(roundtrip.revlog_ids, expanded.revlog_ids);

            let input = ComputeParametersInput {
                model_version: version,
                enable_short_term: true,
                num_relearning_steps: Some(1),
                ..Default::default()
            };
            let old_params = compute_parameters(ComputeParametersInput {
                train_set: expanded.items.clone(),
                card_ids: expanded.card_ids.clone(),
                ..input.clone()
            })?;
            let new_params = compute_compact_parameters(&compact.prefixes, input)?;
            assert_eq!(old_params, new_params);

            let fsrs = FSRS::new(&old_params)?;
            let old_eval = fsrs.evaluate(expanded.items, |_| true)?;
            let new_eval = fsrs.evaluate_from_card_prefixes(&compact.prefixes, |_| true)?;
            assert_eq!(old_eval.log_loss.to_bits(), new_eval.log_loss.to_bits());
            assert_eq!(old_eval.rmse_bins.to_bits(), new_eval.rmse_bins.to_bits());
        }
        Ok(())
    }

    #[test]
    fn calibration_fold_ranges_preserve_final_remainder_and_small_history() {
        assert_eq!(
            time_series_split_ranges(14, 5).collect_vec(),
            vec![2..4, 4..6, 6..8, 8..10, 10..14]
        );
        assert_eq!(
            time_series_split_ranges(6, 5).collect_vec(),
            vec![1..2, 2..3, 3..4, 4..5, 5..6]
        );
        assert!(time_series_split_ranges(5, 5).next().is_none());
        assert!(time_series_split_ranges(14, 0).next().is_none());
    }

    pub(crate) fn review(delta_t: u32) -> FSRSReview {
        FSRSReview {
            rating: 3,
            delta_t: delta_t as f32,
        }
    }

    fn review_f(delta_t: f32) -> FSRSReview {
        FSRSReview { rating: 3, delta_t }
    }

    pub(crate) fn convert_ignore_before(
        revlog: &[RevlogEntry],
        training: bool,
        ignore_before: TimestampMillis,
    ) -> Option<Vec<FSRSItem>> {
        reviews_for_fsrs(revlog.to_vec(), NEXT_DAY_AT, training, ignore_before, false)
            .map(|i| i.fsrs_items.into_iter().map(|(_, item)| item).collect_vec())
    }

    fn convert_with_model(
        revlog: &[RevlogEntry],
        training: bool,
        model_version: ComputeParametersVersion,
    ) -> Option<Vec<FSRSItem>> {
        reviews_for_fsrs(
            revlog.to_vec(),
            NEXT_DAY_AT,
            training,
            0.into(),
            include_same_day_training_entries(model_version, None),
        )
        .map(|i| i.fsrs_items.into_iter().map(|(_, item)| item).collect_vec())
    }

    fn convert_with_model_and_override(
        revlog: &[RevlogEntry],
        training: bool,
        model_version: ComputeParametersVersion,
        include_same_day_reviews: Option<bool>,
    ) -> Option<Vec<FSRSItem>> {
        reviews_for_fsrs(
            revlog.to_vec(),
            NEXT_DAY_AT,
            training,
            0.into(),
            include_same_day_training_entries(model_version, include_same_day_reviews),
        )
        .map(|i| i.fsrs_items.into_iter().map(|(_, item)| item).collect_vec())
    }

    pub(crate) fn convert(revlog: &[RevlogEntry], training: bool) -> Option<Vec<FSRSItem>> {
        convert_ignore_before(revlog, training, 0.into())
    }

    #[test]
    fn legacy_evaluation_with_empty_params_matches_fsrs6_defaults() -> Result<()> {
        let mut col = Collection::new();
        crate::tests::NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.get_first_card().id;
        for entry in [
            revlog_for_card(cid.0, RevlogReviewKind::Learning, 5),
            revlog_for_card(cid.0, RevlogReviewKind::Review, 3),
            revlog_for_card(cid.0, RevlogReviewKind::Review, 1),
        ] {
            col.storage.add_revlog_entry(&entry, false)?;
        }

        let empty = col.evaluate_params_legacy(&vec![], "", 0.into(), None)?;
        let explicit = col.evaluate_params_legacy(
            &fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec(),
            "",
            0.into(),
            None,
        )?;

        assert_eq!(empty.log_loss, explicit.log_loss);
        assert_eq!(empty.rmse_bins, explicit.rmse_bins);
        Ok(())
    }

    #[test]
    fn compute_params_from_prepared_returns_current_params_without_items() -> Result<()> {
        let current_params = fsrs::DEFAULT_PARAMETERS.to_vec();
        let output = compute_params_from_prepared(
            PreparedComputeParams {
                current_params: current_params.clone(),
                num_of_relearning_steps: 1,
                model_version: ComputeParametersVersion::Fsrs7,
                include_same_day_reviews: true,
                enable_scheduling_penalties: true,
                dynamic_desired_retention_enabled: false,
                simulator_config: Default::default(),
                existing_card_input: None,
                training: Default::default(),
                target_counts: TrainingTargetCounts::default(),
            },
            None,
            None,
            false,
        )?;

        assert_eq!(output.params, current_params);
        assert_eq!(output.fsrs_items, 0);
        assert_eq!(output.health_check_passed, None);
        Ok(())
    }

    fn compact_test_items(items: Vec<FSRSItem>) -> CompactTrainingItems {
        let mut compact = CompactTrainingItems::default();
        for (index, item) in items.into_iter().enumerate() {
            let card_index = index as u32;
            let len = item.reviews.len() as u32;
            compact.prefixes.cards.push(CardReviews {
                card_id: index as i64 + 1,
                reviews: item.reviews,
            });
            compact
                .prefixes
                .prefixes
                .push(CardPrefix { card_index, len });
            compact.revlog_ids.push(RevlogId(index as i64 + 1));
        }
        compact
    }

    fn items_failed_after(days: f32, count: usize) -> CompactTrainingItems {
        compact_test_items(vec![
            FSRSItem {
                reviews: vec![
                    FSRSReview {
                        rating: 3,
                        delta_t: 0.0,
                    },
                    FSRSReview {
                        rating: 1,
                        delta_t: days,
                    },
                ],
            };
            count
        ])
    }

    #[test]
    fn optimize_keeps_current_fsrs7_params_when_the_new_ones_fit_worse() -> Result<()> {
        let items = items_failed_after(30.0, 100);
        let good = fsrs::DEFAULT_PARAMETERS.to_vec();
        let mut bad = good.clone();
        bad[2] *= 30.0;

        assert_eq!(params_to_keep(&good, bad.clone(), &items)?, good);
        assert_eq!(params_to_keep(&bad, good.clone(), &items)?, good);
        Ok(())
    }

    #[test]
    fn optimize_keeps_legacy_params_when_too_few_items_to_train() -> Result<()> {
        let legacy = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec();
        let optimized = fsrs::DEFAULT_PARAMETERS.to_vec();

        let few = items_failed_after(30.0, MIN_ITEMS_FOR_TRAINING - 1);
        assert_eq!(params_to_keep(&legacy, optimized.clone(), &few)?, legacy);
        let enough = items_failed_after(30.0, MIN_ITEMS_FOR_TRAINING);
        assert_eq!(
            params_to_keep(&legacy, optimized.clone(), &enough)?,
            optimized
        );
        Ok(())
    }

    fn benchmark_collection_from_env(var: &str) -> Result<Option<(Collection, TempDir, PathBuf)>> {
        let Ok(source) = std::env::var(var) else {
            eprintln!("set {var} to a copied collection.anki2 path to run this benchmark");
            return Ok(None);
        };

        let tempdir = tempdir()?;
        let col_path = tempdir.path().join("feature-bench.anki2");
        std::fs::copy(source, &col_path)?;
        let mut builder = CollectionBuilder::new(&col_path);
        builder.with_desktop_media_paths();
        Ok(Some((builder.build()?, tempdir, col_path)))
    }

    fn benchmark_wal_path(path: &Path) -> PathBuf {
        let mut wal = path.as_os_str().to_os_string();
        wal.push("-wal");
        wal.into()
    }

    fn benchmark_file_size(path: &Path) -> u64 {
        std::fs::metadata(path)
            .map(|metadata| metadata.len())
            .unwrap_or(0)
    }

    fn benchmark_checkpoint_truncate(col: &mut Collection) -> Result<()> {
        col.storage
            .db
            .execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")?;
        Ok(())
    }

    fn benchmark_clear_fsrs_cache_rows(col: &mut Collection) -> Result<()> {
        col.storage.db.execute_batch(&format!(
            "DELETE FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE_FOR_BENCH};"
        ))?;
        Ok(())
    }

    fn benchmark_legacy_autocommit_fsrs_store(
        col: &mut Collection,
        rows: &[FsrsReviewRetrievabilityCacheRow],
        source: &str,
    ) -> Result<usize> {
        if let Some(row) = rows.first() {
            col.storage
                .set_fsrs_review_retrievability_predictions(std::slice::from_ref(row), source)?;
            benchmark_clear_fsrs_cache_rows(col)?;
        }

        let updated_at = TimestampMillis::now().0;
        let mut stored = 0;
        let mut stmt = col.storage.db.prepare_cached(&format!(
            "
            INSERT OR REPLACE INTO {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE_FOR_BENCH}
                (revlog_id, prediction, source, updated_at, sample_role, fold_index)
            VALUES (?, ?, ?, ?, ?, ?)
            "
        ))?;

        for row in rows {
            if row.revlog_id.0 > 0
                && row.prediction.is_finite()
                && (0.0..=1.0).contains(&row.prediction)
            {
                stmt.execute(params![
                    row.revlog_id,
                    row.prediction,
                    source,
                    updated_at,
                    row.sample_role.as_str(),
                    row.fold_index
                ])?;
                stored += 1;
            }
        }

        Ok(stored)
    }

    #[test]
    fn fsrs_retrievability_cache_rows_can_skip_validation_folds() -> Result<()> {
        let mut items = Vec::new();

        for index in 0..12 {
            let reviews = vec![
                FSRSReview {
                    rating: 3,
                    delta_t: 0.0,
                },
                FSRSReview {
                    rating: if index % 2 == 0 { 3 } else { 1 },
                    delta_t: 1.0 + index as f32,
                },
            ];
            items.push(FSRSItem { reviews });
        }

        let context = FsrsReviewPredictionContext {
            training: compact_test_items(items),
            model_version: ComputeParametersVersion::Fsrs7,
            num_relearning_steps: 1,
            enable_scheduling_penalties: false,
        };
        let final_fit_rows = fsrs_review_retrievability_cache_rows(
            &fsrs::DEFAULT_PARAMETERS,
            &context,
            false,
            None,
        )?;
        assert_eq!(
            final_fit_rows.iter().map(|row| row.revlog_id).collect_vec(),
            context.training.revlog_ids
        );
        assert!(final_fit_rows.iter().all(|row| row.sample_role
            == FsrsReviewRetrievabilitySampleRole::FinalFit
            && row.fold_index == -1));

        let validation_rows =
            fsrs_review_retrievability_cache_rows(&fsrs::DEFAULT_PARAMETERS, &context, true, None)?;
        assert_eq!(
            validation_rows
                .iter()
                .filter(|row| row.sample_role == FsrsReviewRetrievabilitySampleRole::ValidationFold)
                .map(|row| (row.revlog_id, row.fold_index))
                .collect_vec(),
            (3..=12)
                .map(|id| (RevlogId(id), ((id - 3) / 2) as i32))
                .collect_vec()
        );
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_cache_rows_preserve_predictions_and_review_ids() -> Result<()> {
        let mut same_day_review = revlog_for_card(1, RevlogReviewKind::Relearning, 9);
        same_day_review.id.0 += 120_000;
        let mut lapse = revlog_for_card(1, RevlogReviewKind::Review, 9);
        // Keep both reviews after rollover, rather than straddling the boundary.
        lapse.id.0 += 60_000;
        lapse.button_chosen = 1;
        let revlogs = vec![
            revlog_for_card(1, RevlogReviewKind::Learning, 10),
            lapse,
            same_day_review.clone(),
            revlog_for_card(1, RevlogReviewKind::Review, 1),
            revlog_for_card(2, RevlogReviewKind::Learning, 9),
            revlog_for_card(2, RevlogReviewKind::Review, 8),
            revlog_for_card(2, RevlogReviewKind::Review, 2),
        ];

        for params in [
            &[][..],
            &fsrs::FSRS6_DEFAULT_PARAMETERS[..],
            &fsrs::DEFAULT_PARAMETERS[..],
        ] {
            let model_version = model_version_for_params(params);
            let include_same_day = include_same_day_training_entries(model_version, None);
            let expanded =
                fsrs_items_for_training(revlogs.clone(), NEXT_DAY_AT, 0.into(), include_same_day);
            let expected = FSRS::new(legacy_fsrs_params(params))?
                .predict_retrievability_with_card_ids(
                    expanded.items,
                    expanded.card_ids.unwrap(),
                    |_| true,
                )?;
            let context = FsrsReviewPredictionContext {
                training: compact_fsrs_items_for_training(
                    revlogs.clone(),
                    NEXT_DAY_AT,
                    0.into(),
                    include_same_day,
                )?,
                model_version,
                num_relearning_steps: 1,
                enable_scheduling_penalties: false,
            };
            assert_eq!(context.training.revlog_ids, expanded.revlog_ids.unwrap());

            let rows = fsrs_review_retrievability_cache_rows(params, &context, false, None)?;

            assert_eq!(
                rows.iter().map(|row| row.revlog_id).collect_vec(),
                context.training.revlog_ids
            );
            assert_eq!(
                rows.iter().map(|row| row.prediction).collect_vec(),
                expected,
                "parameter count: {}",
                params.len()
            );
            assert_eq!(
                rows.iter().any(|row| row.revlog_id == same_day_review.id),
                include_same_day
            );
        }
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_cache_rows_return_no_rows_without_targets() -> Result<()> {
        let context = FsrsReviewPredictionContext {
            training: Default::default(),
            model_version: ComputeParametersVersion::Fsrs7,
            num_relearning_steps: 1,
            enable_scheduling_penalties: false,
        };

        let rows = fsrs_review_retrievability_cache_rows(&[], &context, true, None)?;

        assert!(rows.is_empty());
        Ok(())
    }

    #[test]
    fn fsrs6_comparison_returns_separate_saved_and_held_out_predictions() -> Result<()> {
        let tempdir = tempdir()?;
        let backend = Backend::new(I18n::template_only(), false);
        backend.open_collection(anki_proto::collection::OpenCollectionRequest {
            collection_path: tempdir
                .path()
                .join("collection.anki2")
                .to_str()
                .unwrap()
                .into(),
            media_folder_path: tempdir.path().join("media").to_str().unwrap().into(),
            media_db_path: tempdir.path().join("media.db").to_str().unwrap().into(),
        })?;
        let context = backend.with_col(|col| {
            crate::tests::NoteAdder::basic(col).add(col);
            let cid = col.get_first_card().id;
            col.storage.add_revlog_entry(
                &revlog_for_card(cid.0, RevlogReviewKind::Learning, 20),
                false,
            )?;
            for days_ago in (1..=12).rev() {
                col.storage.add_revlog_entry(
                    &RevlogEntry {
                        button_chosen: if days_ago % 2 == 0 { 3 } else { 1 },
                        ..revlog_for_card(cid.0, RevlogReviewKind::Review, days_ago)
                    },
                    false,
                )?;
            }
            let prepared = col.prepare_compute_params(PrepareComputeParamsInput {
                search: "",
                ignore_revlogs_before: 0.into(),
                current_params: &fsrs::FSRS6_DEFAULT_PARAMETERS,
                num_of_relearning_steps: 1,
                include_same_day_reviews: None,
                enable_scheduling_penalties: false,
                model_version_override: Some(ComputeParametersVersion::Fsrs6),
                dynamic_desired_retention_enabled: false,
                historical_retention: 0.9,
                desired_retention: 0.9,
                dynamic_desired_retention_simulator_options:
                    DynamicDesiredRetentionSimulatorOptions::default(),
            })?;
            Ok(FsrsReviewPredictionContext::from_prepared(&prepared))
        })?;
        let expected = fsrs_review_retrievability_cache_rows(
            &fsrs::FSRS6_DEFAULT_PARAMETERS,
            &context,
            true,
            None,
        )?;
        let result = backend.compute_fsrs_six_comparison(
            anki_proto::scheduler::ComputeFsrs6ComparisonRequest {
                params: fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec(),
                num_of_relearning_steps: 1,
                include_validation_folds: true,
                ..Default::default()
            },
        )?;
        for (bytes, role) in [
            (
                &result.final_fit,
                FsrsReviewRetrievabilitySampleRole::FinalFit,
            ),
            (
                &result.validation_fold,
                FsrsReviewRetrievabilitySampleRole::ValidationFold,
            ),
        ] {
            let mut expected = expected
                .iter()
                .filter(|row| row.sample_role == role)
                .map(|row| (row.revlog_id.0, row.prediction))
                .collect_vec();
            expected.sort_by_key(|row| row.0);
            let actual = bytes
                .as_chunks::<12>()
                .0
                .iter()
                .map(|chunk| {
                    (
                        i64::from_le_bytes(chunk[..8].try_into().unwrap()),
                        f32::from_le_bytes(chunk[8..].try_into().unwrap()),
                    )
                })
                .collect_vec();
            assert!(!actual.is_empty());
            assert_eq!(actual, expected);
        }
        assert_eq!(result.history_fingerprint.len(), 64);
        Ok(())
    }

    #[test]
    fn fsrs_calibration_backend_stores_same_rows_as_collection_computation() -> Result<()> {
        for include_validation_folds in [false, true] {
            let tempdir = tempdir()?;
            let backend = Backend::new(I18n::template_only(), false);
            backend.open_collection(anki_proto::collection::OpenCollectionRequest {
                collection_path: tempdir
                    .path()
                    .join("collection.anki2")
                    .to_str()
                    .unwrap()
                    .into(),
                media_folder_path: tempdir.path().join("media").to_str().unwrap().into(),
                media_db_path: tempdir.path().join("media.db").to_str().unwrap().into(),
            })?;
            let context = backend.with_col(|col| {
                crate::tests::NoteAdder::basic(col).add(col);
                let cid = col.get_first_card().id;
                col.storage.add_revlog_entry(
                    &revlog_for_card(cid.0, RevlogReviewKind::Learning, 20),
                    false,
                )?;
                for days_ago in (1..=12).rev() {
                    let entry = RevlogEntry {
                        button_chosen: if days_ago % 2 == 0 { 3 } else { 1 },
                        ..revlog_for_card(cid.0, RevlogReviewKind::Review, days_ago)
                    };
                    col.storage.add_revlog_entry(&entry, false)?;
                }
                let prepared = col.prepare_compute_params(PrepareComputeParamsInput {
                    search: "",
                    ignore_revlogs_before: 0.into(),
                    current_params: &fsrs::DEFAULT_PARAMETERS,
                    num_of_relearning_steps: 1,
                    include_same_day_reviews: Some(true),
                    enable_scheduling_penalties: false,
                    model_version_override: Some(ComputeParametersVersion::Fsrs7),
                    dynamic_desired_retention_enabled: false,
                    historical_retention: 0.9,
                    desired_retention: 0.9,
                    dynamic_desired_retention_simulator_options:
                        DynamicDesiredRetentionSimulatorOptions::default(),
                })?;
                Ok(FsrsReviewPredictionContext::from_prepared(&prepared))
            })?;
            let expected_rows = fsrs_review_retrievability_cache_rows(
                &fsrs::DEFAULT_PARAMETERS,
                &context,
                include_validation_folds,
                None,
            )?;
            assert_eq!(
                expected_rows
                    .iter()
                    .filter(|row| row.sample_role == FsrsReviewRetrievabilitySampleRole::FinalFit)
                    .count(),
                12
            );
            assert_eq!(
                expected_rows.iter().any(|row| {
                    row.sample_role == FsrsReviewRetrievabilitySampleRole::ValidationFold
                }),
                include_validation_folds
            );

            backend.set_wants_abort()?;
            let stored = if include_validation_folds {
                backend.rebuild_fsrs_review_retrievability_cache()?
            } else {
                backend.compute_fsrs_review_retrievability_calibration(
                    anki_proto::scheduler::ComputeFsrsReviewRetrievabilityCalibrationRequest {
                        params: fsrs::DEFAULT_PARAMETERS.to_vec(),
                        num_of_relearning_steps: 1,
                        include_same_day_reviews: Some(true),
                        include_validation_folds,
                        ..Default::default()
                    },
                )?
            };
            assert_eq!(stored.val as usize, expected_rows.len());
            backend.with_col(|col| {
                let count: usize = col.storage.db.query_row(
                    "SELECT count(*) FROM search_stats_fsrs_review_retrievability",
                    [],
                    |row| row.get(0),
                )?;
                assert_eq!(count, expected_rows.len());
                for expected in &expected_rows {
                    let stored: (f32, String) = col.storage.db.query_row(
                        "SELECT prediction, source FROM search_stats_fsrs_review_retrievability
                         WHERE revlog_id = ? AND sample_role = ? AND fold_index = ?",
                        params![
                            expected.revlog_id,
                            expected.sample_role.as_str(),
                            expected.fold_index
                        ],
                        |row| Ok((row.get(0)?, row.get(1)?)),
                    )?;
                    assert_eq!(
                        stored,
                        (expected.prediction, "fsrs_calibration_recompute".into())
                    );
                }
                Ok(())
            })?;
        }
        Ok(())
    }

    #[test]
    #[ignore]
    fn fsrs_optimize_cache_store_feature_benchmark() -> Result<()> {
        let Some((mut col, _tempdir, col_path)) =
            benchmark_collection_from_env("ANKI_FEATURE_BENCH_COLLECTION")?
        else {
            return Ok(());
        };
        let search = std::env::var("ANKI_FEATURE_BENCH_SEARCH").unwrap_or_default();
        let wal_path = benchmark_wal_path(&col_path);

        let prepare_started = Instant::now();
        let prepared = col.prepare_compute_params(PrepareComputeParamsInput {
            search: &search,
            ignore_revlogs_before: TimestampMillis(0),
            current_params: &fsrs::DEFAULT_PARAMETERS,
            num_of_relearning_steps: 1,
            include_same_day_reviews: Some(true),
            enable_scheduling_penalties: false,
            model_version_override: Some(ComputeParametersVersion::Fsrs7),
            dynamic_desired_retention_enabled: false,
            historical_retention: 0.9,
            desired_retention: 0.9,
            dynamic_desired_retention_simulator_options:
                DynamicDesiredRetentionSimulatorOptions::default(),
        })?;
        let prepared_items = prepared.training.len();
        let prepared_targets = prepared.target_counts.total_targets;
        let eval_items = prepared.training.prefixes.clone();
        let fsrs_prediction_context = FsrsReviewPredictionContext::from_prepared(&prepared);
        let prepare_ms = prepare_started.elapsed().as_secs_f64() * 1000.0;

        let optimize_started = Instant::now();
        let output = compute_params_from_prepared(prepared, None, None, false)?;
        let optimize_ms = optimize_started.elapsed().as_secs_f64() * 1000.0;

        let eval_started = Instant::now();
        let eval = FSRS::new(&output.params)?.evaluate_from_card_prefixes(&eval_items, |_| true)?;
        let eval_ms = eval_started.elapsed().as_secs_f64() * 1000.0;

        let rows_started = Instant::now();
        let predictions = fsrs_review_retrievability_cache_rows(
            &output.params,
            &fsrs_prediction_context,
            true,
            None,
        )?;
        let rows_ms = rows_started.elapsed().as_secs_f64() * 1000.0;

        if predictions.is_empty() {
            println!(
                "fsrs_optimize_cache_store_feature_benchmark search={search:?} \
                 prepared_items={prepared_items} prepared_targets={prepared_targets} \
                 cache_rows=0 prepare_ms={prepare_ms:.3} optimize_ms={optimize_ms:.3} \
                 eval_ms={eval_ms:.3} log_loss={:.9} rmse_bins={:.9} rows_ms={rows_ms:.3}",
                eval.log_loss, eval.rmse_bins
            );
            return Ok(());
        }

        benchmark_checkpoint_truncate(&mut col)?;
        let legacy_started = Instant::now();
        let legacy_stored =
            benchmark_legacy_autocommit_fsrs_store(&mut col, &predictions, "fsrs_feature_bench")?;
        let legacy_ms = legacy_started.elapsed().as_secs_f64() * 1000.0;
        let legacy_wal_bytes = benchmark_file_size(&wal_path);

        benchmark_clear_fsrs_cache_rows(&mut col)?;
        benchmark_checkpoint_truncate(&mut col)?;
        let batched_started = Instant::now();
        let batched_stored = col
            .storage
            .set_fsrs_review_retrievability_predictions(&predictions, "fsrs_feature_bench")?;
        let batched_ms = batched_started.elapsed().as_secs_f64() * 1000.0;
        let batched_wal_bytes = benchmark_file_size(&wal_path);

        benchmark_checkpoint_truncate(&mut col)?;
        let repeated_started = Instant::now();
        let repeated_stored = col
            .storage
            .set_fsrs_review_retrievability_predictions(&predictions, "fsrs_feature_bench")?;
        let repeated_ms = repeated_started.elapsed().as_secs_f64() * 1000.0;
        let repeated_wal_bytes = benchmark_file_size(&wal_path);

        let legacy_total_ms = prepare_ms + optimize_ms + rows_ms + legacy_ms;
        let batched_total_ms = prepare_ms + optimize_ms + rows_ms + batched_ms;
        let repeated_total_ms = prepare_ms + optimize_ms + rows_ms + repeated_ms;

        println!(
            "fsrs_optimize_cache_store_feature_benchmark search={search:?} \
             prepared_items={prepared_items} prepared_targets={prepared_targets} \
             cache_rows={} prepare_ms={prepare_ms:.3} optimize_ms={optimize_ms:.3} \
             eval_ms={eval_ms:.3} log_loss={:.9} rmse_bins={:.9} rows_ms={rows_ms:.3} \
             legacy_stored={legacy_stored} legacy_store_ms={legacy_ms:.3} \
             legacy_wal_bytes={legacy_wal_bytes} batched_stored={batched_stored} \
             batched_store_ms={batched_ms:.3} batched_wal_bytes={batched_wal_bytes} \
             repeated_stored={repeated_stored} repeated_store_ms={repeated_ms:.3} \
             repeated_wal_bytes={repeated_wal_bytes} legacy_total_ms={legacy_total_ms:.3} \
             batched_total_ms={batched_total_ms:.3} repeated_total_ms={repeated_total_ms:.3}",
            predictions.len(),
            eval.log_loss,
            eval.rmse_bins
        );

        assert_eq!(legacy_stored, predictions.len());
        assert_eq!(batched_stored, predictions.len());
        assert_eq!(repeated_stored, predictions.len());
        Ok(())
    }

    #[test]
    fn dynamic_desired_retention_calibration_keeps_weights_and_avg_drs() -> Result<()> {
        let calibration = dynamic_desired_retention_calibration_from_parts(
            vec![1.0; 15],
            [(0.0, Some(0.9)), (16.0, None), (64.0, Some(0.8))],
            [(0.0, 0.91), (64.0, 0.81)],
            [(16.0, 0.9), (64.0, 0.8)],
            DynamicDesiredRetentionBounds {
                retention_min: 0.75,
                retention_max: 0.95,
            },
        )?;

        assert_eq!(calibration.params, vec![1.0; 15]);
        assert_eq!(calibration.weights, vec![0.0, 64.0]);
        assert_eq!(calibration.avg_drs, vec![0.9, 0.8]);
        assert_eq!(calibration.fsrs_eq_weights, vec![0.0, 64.0]);
        assert_eq!(calibration.fsrs_eq_drs, vec![0.91, 0.81]);
        assert_eq!(calibration.fixed_target_weights, vec![16.0, 64.0]);
        assert_eq!(calibration.fixed_target_drs, vec![0.9, 0.8]);
        assert_eq!(calibration.retention_min, 0.75);
        assert_eq!(calibration.retention_max, 0.95);
        Ok(())
    }

    #[test]
    fn dynamic_desired_retention_uses_default_bounds() -> Result<()> {
        let bounds = default_dynamic_desired_retention_bounds();

        assert_eq!(bounds.retention_min, DEFAULT_RETENTION_MIN);
        assert_eq!(bounds.retention_max, DEFAULT_RETENTION_MAX);
        Ok(())
    }

    #[test]
    fn prepare_params_keeps_review_statistics_when_dynamic_retention_is_enabled() -> Result<()> {
        let mut col = Collection::new();
        crate::tests::NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.get_first_card().id;
        let mut learning = revlog_for_card(cid.0, RevlogReviewKind::Learning, 10);
        learning.taken_millis = 70_000;
        let mut review = revlog_for_card(cid.0, RevlogReviewKind::Review, 5);
        review.taken_millis = 70_000;
        col.storage.add_revlog_entry(&learning, false)?;
        col.storage.add_revlog_entry(&review, false)?;

        let prepared = col.prepare_compute_params(PrepareComputeParamsInput {
            search: "",
            ignore_revlogs_before: 0.into(),
            current_params: &fsrs::DEFAULT_PARAMETERS,
            num_of_relearning_steps: 1,
            include_same_day_reviews: Some(true),
            enable_scheduling_penalties: false,
            model_version_override: Some(ComputeParametersVersion::Fsrs7),
            dynamic_desired_retention_enabled: true,
            historical_retention: 0.9,
            desired_retention: 0.9,
            dynamic_desired_retention_simulator_options:
                DynamicDesiredRetentionSimulatorOptions::default(),
        })?;

        assert_eq!(prepared.simulator_config.deck_size, 1);
        assert!(prepared.simulator_config.review_rating_cost_fn.is_some());
        assert_ne!(
            prepared.simulator_config.state_rating_costs[1][2],
            SimulatorConfig::default().state_rating_costs[1][2]
        );
        Ok(())
    }

    #[test]
    fn dynamic_desired_retention_simulator_uses_selected_slice_shape() {
        let mut card_one_learning = revlog(RevlogReviewKind::Learning, 10);
        card_one_learning.cid = CardId(1);
        let mut card_one_review = revlog(RevlogReviewKind::Review, 9);
        card_one_review.cid = CardId(1);
        let mut card_two_learning = revlog(RevlogReviewKind::Learning, 4);
        card_two_learning.cid = CardId(2);
        let mut card_two_review = revlog(RevlogReviewKind::Review, 2);
        card_two_review.cid = CardId(2);
        let mut config = SimulatorConfig::default();

        shape_simulator_config_for_dynamic_desired_retention(
            &mut config,
            &[
                card_one_learning,
                card_one_review,
                card_two_learning,
                card_two_review,
            ],
            NEXT_DAY_AT.0,
            DynamicDesiredRetentionSimulatorOptions::default(),
        )
        .unwrap();

        assert_eq!(config.deck_size, 2);
        assert_eq!(config.learn_span, 2);
        assert_eq!(config.learn_limit, 1);
        assert_eq!(config.review_limit, DYNAMIC_DR_DEFAULT_REVIEW_LIMIT);
        assert_eq!(
            config.max_cost_perday,
            DYNAMIC_DR_DEFAULT_MAX_COST_PERDAY_MINUTES * 60.0
        );
    }

    #[test]
    fn dynamic_desired_retention_simulator_uses_limit_overrides() {
        let mut card_learning = revlog(RevlogReviewKind::Learning, 10);
        card_learning.cid = CardId(1);
        let mut config = SimulatorConfig::default();

        shape_simulator_config_for_dynamic_desired_retention(
            &mut config,
            &[card_learning],
            NEXT_DAY_AT.0,
            DynamicDesiredRetentionSimulatorOptions {
                review_limit: Some(123),
                max_cost_perday_minutes: Some(45.0),
            },
        )
        .unwrap();

        assert_eq!(config.review_limit, 123);
        assert_eq!(config.max_cost_perday, 45.0 * 60.0);
    }

    #[test]
    fn dynamic_desired_retention_installs_review_time_cost_fn() -> Result<()> {
        let mut config = SimulatorConfig {
            state_rating_costs: [
                [1.0, 2.0, 3.0, 4.0],
                [11.0, 12.0, 13.0, 14.0],
                [21.0, 22.0, 23.0, 24.0],
            ],
            ..Default::default()
        };

        install_dynamic_desired_retention_review_time_cost_fn(
            &mut config,
            &[],
            &fsrs::DEFAULT_PARAMETERS,
            NEXT_DAY_AT,
        )?;

        let cost_fn = config.review_rating_cost_fn.as_ref().unwrap();
        assert_eq!(cost_fn(&fsrs::Card::default(), 3, 0.8), 13.0);
        Ok(())
    }

    #[macro_export]
    macro_rules! fsrs_items {
        ($($reviews:expr),*) => {
            Some(vec![
                $(
                    FSRSItem {
                        reviews: $reviews.to_vec()
                    }
                ),*
            ])
        };
    }

    #[test]
    fn delta_t_is_correct() -> Result<()> {
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 1),
                    revlog(RevlogReviewKind::Review, 0)
                ],
                true,
            ),
            fsrs_items!([review(0), review(1)])
        );
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 15),
                    revlog(RevlogReviewKind::Learning, 13),
                    revlog(RevlogReviewKind::Review, 10),
                    revlog(RevlogReviewKind::Review, 5)
                ],
                true,
            ),
            fsrs_items!(
                [review(0), review(2)],
                [review(0), review(2), review(3)],
                [review(0), review(2), review(3), review(5)]
            )
        );
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 15),
                    revlog(RevlogReviewKind::Learning, 13),
                ],
                true,
            ),
            fsrs_items!([review(0), review(2),])
        );
        Ok(())
    }

    #[test]
    fn card_ids_align_with_sorted_training_items() {
        let training_items = fsrs_items_for_training(
            vec![
                revlog_for_card(1, RevlogReviewKind::Learning, 10),
                revlog_for_card(1, RevlogReviewKind::Review, 7),
                revlog_for_card(1, RevlogReviewKind::Review, 1),
                revlog_for_card(2, RevlogReviewKind::Learning, 9),
                revlog_for_card(2, RevlogReviewKind::Review, 8),
            ],
            NEXT_DAY_AT,
            0.into(),
            true,
        );

        assert_eq!(training_items.card_ids.as_deref(), Some(&[2, 1, 1][..]));
        assert_eq!(
            training_items
                .items
                .iter()
                .map(|item| item.reviews.len())
                .collect_vec(),
            vec![2, 2, 3]
        );
    }

    #[test]
    fn cram_is_filtered() {
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    revlog(RevlogReviewKind::Review, 9),
                    revlog(RevlogReviewKind::Filtered, 7),
                    revlog(RevlogReviewKind::Review, 4),
                ],
                true,
            ),
            fsrs_items!([review(0), review(1)], [review(0), review(1), review(5)])
        );
    }

    #[test]
    fn set_due_date_is_filtered() {
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    revlog(RevlogReviewKind::Review, 9),
                    RevlogEntry {
                        ease_factor: 100,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    revlog(RevlogReviewKind::Review, 4),
                ],
                true,
            ),
            fsrs_items!([review(0), review(1)], [review(0), review(1), review(5)])
        );
    }

    #[test]
    fn card_reset_drops_all_previous_history() {
        // If Reset comes in between two Learn entries, only the ones after the Reset
        // are used.
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    RevlogEntry {
                        ease_factor: 0,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    revlog(RevlogReviewKind::Learning, 4),
                    revlog(RevlogReviewKind::Review, 0),
                ],
                true,
            ),
            fsrs_items!([review(0), review(4)])
        );
        // Return None if Reset is the last entry or is followed by only manual entries.
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    revlog(RevlogReviewKind::Review, 9),
                    RevlogEntry {
                        ease_factor: 0,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    RevlogEntry {
                        ease_factor: 100,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                ],
                false,
            ),
            None,
        );
        // If non-learning user-graded entries are found after Reset, return None during
        // training but return the remaining entries during memory state calculation.
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    revlog(RevlogReviewKind::Review, 9),
                    RevlogEntry {
                        ease_factor: 0,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    revlog(RevlogReviewKind::Review, 1),
                    revlog(RevlogReviewKind::Relearning, 0),
                ],
                true,
            ),
            None,
        );
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Review, 9),
                    RevlogEntry {
                        ease_factor: 0,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    revlog(RevlogReviewKind::Review, 1),
                    revlog(RevlogReviewKind::Relearning, 0),
                ],
                false,
            ),
            fsrs_items!([review(0), review(1)])
        );
    }

    #[test]
    fn coerce_computed_params_prefers_computed_when_matches_selected_family() {
        let current = vec![1.0; 21];
        let computed = vec![2.0; 34];
        assert_eq!(
            coerce_computed_params_to_selected_version(
                ComputeParametersVersion::Fsrs7,
                &current,
                computed.clone()
            ),
            computed
        );
    }

    #[test]
    fn coerce_computed_params_keeps_computed_when_lengths_match() {
        let current = vec![1.0; 21];
        let computed = vec![2.0; 21];
        assert_eq!(
            coerce_computed_params_to_selected_version(
                ComputeParametersVersion::Fsrs6,
                &current,
                computed.clone()
            ),
            computed
        );
    }

    #[test]
    fn coerce_computed_params_falls_back_to_current_when_computed_invalid_for_selected_family() {
        let current = vec![1.0; 34];
        let computed = vec![2.0; 21];
        assert_eq!(
            coerce_computed_params_to_selected_version(
                ComputeParametersVersion::Fsrs7,
                &current,
                computed
            ),
            current
        );
    }

    #[test]
    fn single_learning_step_skipped_when_training() {
        assert_eq!(
            convert(&[revlog(RevlogReviewKind::Learning, 1),], true),
            None,
        );
        assert_eq!(
            convert(&[revlog(RevlogReviewKind::Learning, 1),], false),
            fsrs_items!([review(0)])
        );
    }

    #[test]
    fn fsrs7_training_includes_same_day_only_targets() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 1),
            revlog(RevlogReviewKind::Review, 1),
            revlog(RevlogReviewKind::Review, 1),
        ];
        assert_eq!(
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs6),
            None
        );
        assert_eq!(
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs7),
            Some(vec![
                FSRSItem {
                    reviews: vec![review(0), review_f(1.0 / 86_400_000.0)],
                },
                FSRSItem {
                    reviews: vec![
                        review(0),
                        review_f(1.0 / 86_400_000.0),
                        review_f(1.0 / 86_400_000.0),
                    ],
                },
            ])
        );
    }

    #[test]
    fn fsrs7_training_can_ignore_same_day_targets_with_override() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 1),
            revlog(RevlogReviewKind::Review, 1),
            revlog(RevlogReviewKind::Review, 1),
        ];
        assert_eq!(
            convert_with_model_and_override(
                revlogs,
                true,
                ComputeParametersVersion::Fsrs7,
                Some(false),
            ),
            None
        );
    }

    #[test]
    fn fsrs6_training_ignores_same_day_true_override() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 1),
            revlog(RevlogReviewKind::Review, 1),
            revlog(RevlogReviewKind::Review, 1),
        ];
        assert_eq!(
            convert_with_model_and_override(
                revlogs,
                true,
                ComputeParametersVersion::Fsrs6,
                Some(true),
            ),
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs6)
        );
    }

    #[test]
    fn fsrs7_training_toggle_true_path_is_unchanged() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 1),
            revlog(RevlogReviewKind::Review, 1),
            revlog(RevlogReviewKind::Review, 1),
        ];
        assert_eq!(
            convert_with_model_and_override(
                revlogs,
                true,
                ComputeParametersVersion::Fsrs7,
                Some(true),
            ),
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs7)
        );
    }

    #[test]
    fn filtered_empty_dataset_returns_not_enough_data() {
        let filtered = filter_non_same_day_evaluation_targets(vec![FSRSItem {
            reviews: vec![review(0), review_f(0.5)],
        }]);
        assert!(filtered.is_empty());
        let err = evaluate_with_time_series_splits(
            ComputeParametersInput {
                training_config: None,
                train_set: filtered,
                card_ids: None,
                progress: None,
                enable_short_term: true,
                enable_sched_penalties: true,
                model_version: ComputeParametersVersion::Fsrs7,
                num_relearning_steps: Some(1),
            },
            |_| true,
        )
        .unwrap_err();
        assert!(matches!(err, fsrs::FSRSError::NotEnoughData));
    }

    #[test]
    fn health_check_adjustment_uses_filtered_target_counts() {
        let mut items = vec![];
        for _ in 0..19 {
            items.push(FSRSItem {
                reviews: vec![review(0), review(2)],
            });
        }
        items.push(FSRSItem {
            reviews: vec![
                review(0),
                FSRSReview {
                    rating: 1,
                    delta_t: 2.0,
                },
            ],
        });
        for _ in 0..4 {
            items.push(FSRSItem {
                reviews: vec![
                    review(0),
                    FSRSReview {
                        rating: 1,
                        delta_t: 0.5,
                    },
                ],
            });
        }

        let filtered = filter_non_same_day_evaluation_targets(items.clone());
        assert_eq!(filtered.len(), 20);
        let eval = ModelEvaluation {
            log_loss: 0.3,
            rmse_bins: 1.0,
        };
        assert!(HealthCheckTargets::from_items(&items).passed(eval));
        assert!(!HealthCheckTargets::from_items(&filtered).passed(eval));
    }

    #[test]
    fn fsrs7_training_keeps_same_day_targets_after_long_term_review() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 3),
            revlog(RevlogReviewKind::Review, 2),
            revlog(RevlogReviewKind::Review, 2),
        ];
        assert_eq!(
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs6),
            fsrs_items!([review(0), review(1)])
        );
        assert_eq!(
            convert_with_model(revlogs, true, ComputeParametersVersion::Fsrs7),
            Some(vec![
                FSRSItem {
                    reviews: vec![review(0), review(1)],
                },
                FSRSItem {
                    reviews: vec![review(0), review(1), review_f(1.0 / 86_400_000.0)],
                },
            ])
        );
    }

    #[test]
    fn fsrs_items_for_training_keeps_items_without_long_term_review_for_fsrs7() {
        let revlogs = vec![
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Learning, 1)
            },
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Review, 1)
            },
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Review, 1)
            },
        ];
        let items = fsrs_items_for_training(
            revlogs,
            NEXT_DAY_AT,
            TimestampMillis(0),
            include_same_day_training_entries(ComputeParametersVersion::Fsrs7, None),
        );
        assert_eq!(
            items.items,
            vec![
                FSRSItem {
                    reviews: vec![review(0), review_f(1.0 / 86_400_000.0)],
                },
                FSRSItem {
                    reviews: vec![
                        review(0),
                        review_f(1.0 / 86_400_000.0),
                        review_f(1.0 / 86_400_000.0),
                    ],
                },
            ]
        );
        assert_eq!(items.card_ids, Some(vec![1, 1]));
    }

    #[test]
    fn fsrs_items_for_training_reports_long_and_same_day_target_counts() {
        let revlogs = vec![
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Learning, 3)
            },
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Review, 2)
            },
            RevlogEntry {
                cid: CardId(1),
                ..revlog(RevlogReviewKind::Review, 2)
            },
        ];
        let fsrs6_items = fsrs_items_for_training(
            revlogs.clone(),
            NEXT_DAY_AT,
            TimestampMillis(0),
            include_same_day_training_entries(ComputeParametersVersion::Fsrs6, None),
        );
        let fsrs6_counts = fsrs6_items.target_counts();
        assert_eq!(fsrs6_counts.total_targets, 1);
        assert_eq!(fsrs6_counts.long_term_targets, 1);
        assert_eq!(fsrs6_counts.short_term_targets, 0);

        let fsrs7_items = fsrs_items_for_training(
            revlogs,
            NEXT_DAY_AT,
            TimestampMillis(0),
            include_same_day_training_entries(ComputeParametersVersion::Fsrs7, None),
        );
        let fsrs7_counts = fsrs7_items.target_counts();
        assert_eq!(fsrs7_counts.total_targets, 2);
        assert_eq!(fsrs7_counts.long_term_targets, 1);
        assert_eq!(fsrs7_counts.short_term_targets, 1);
    }

    #[test]
    fn fsrs7_same_day_delta_uses_fractional_elapsed_time() {
        let base = days_ago_ms(1).0 + 3_600_000;
        let revlogs = vec![
            RevlogEntry {
                id: RevlogId(base),
                ..revlog(RevlogReviewKind::Learning, 1)
            },
            RevlogEntry {
                id: RevlogId(base + 3_600_000),
                ..revlog(RevlogReviewKind::Review, 1)
            },
        ];
        let converted =
            convert_with_model(&revlogs, true, ComputeParametersVersion::Fsrs7).unwrap();
        let delta = converted[0].reviews[1].delta_t;
        assert!(delta > 0.0);
        assert!((delta - (1.0 / 24.0)).abs() < 1e-6);
    }

    #[test]
    fn fsrs7_same_day_delta_is_positive_when_timestamps_equal() {
        let base = days_ago_ms(1).0;
        let revlogs = vec![
            RevlogEntry {
                id: RevlogId(base),
                ..revlog(RevlogReviewKind::Learning, 1)
            },
            RevlogEntry {
                id: RevlogId(base),
                ..revlog(RevlogReviewKind::Review, 1)
            },
        ];
        let converted =
            convert_with_model(&revlogs, true, ComputeParametersVersion::Fsrs7).unwrap();
        assert!(converted[0].reviews[1].delta_t > 0.0);
    }

    #[test]
    fn fsrs7_interday_delta_uses_fractional_elapsed_time() {
        let revlogs = vec![
            RevlogEntry {
                id: RevlogId(days_ago_ms(3).0),
                ..revlog(RevlogReviewKind::Learning, 3)
            },
            RevlogEntry {
                // 0.5 day after the D-1 boundary -> elapsed days differs from elapsed timestamp.
                id: RevlogId(days_ago_ms(1).0 + 43_200_000),
                ..revlog(RevlogReviewKind::Review, 1)
            },
        ];
        let converted6 =
            convert_with_model(&revlogs, true, ComputeParametersVersion::Fsrs6).unwrap();
        let converted7 =
            convert_with_model(&revlogs, true, ComputeParametersVersion::Fsrs7).unwrap();
        assert_eq!(converted6[0].reviews[1].delta_t, 3.0);
        assert!((converted7[0].reviews[1].delta_t - 2.5).abs() < 1e-6);
    }

    #[test]
    fn resolved_model_version_prefers_override() {
        assert_eq!(
            super::resolved_model_version(&[0.0; 21], Some(ComputeParametersVersion::Fsrs7)),
            ComputeParametersVersion::Fsrs7
        );
        assert_eq!(
            super::resolved_model_version(&[0.0; 34], Some(ComputeParametersVersion::Fsrs6)),
            ComputeParametersVersion::Fsrs6
        );
    }

    #[test]
    fn resolved_model_version_falls_back_to_param_length() {
        assert_eq!(
            super::resolved_model_version(&[0.0; 34], None),
            ComputeParametersVersion::Fsrs7
        );
        assert_eq!(
            super::resolved_model_version(&[0.0; 21], None),
            ComputeParametersVersion::Fsrs6
        );
    }

    #[test]
    fn ignores_cards_before_ignore_before_date_when_training() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 10),
            revlog(RevlogReviewKind::Learning, 8),
        ];
        // | = Ignore before
        // L = learning step
        // L L |
        assert_eq!(convert_ignore_before(revlogs, true, days_ago_ms(7)), None);
        // L | L
        assert_eq!(convert_ignore_before(revlogs, true, days_ago_ms(9)), None);
        // L (|L) (exact same millisecond)
        assert_eq!(
            convert_ignore_before(revlogs, true, days_ago_ms(10)),
            convert(revlogs, true)
        );
        // | L L
        assert_eq!(
            convert_ignore_before(revlogs, true, days_ago_ms(11)),
            convert(revlogs, true)
        );
    }

    #[test]
    fn partially_ignored_learning_steps_terminate_training() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 10),
            revlog(RevlogReviewKind::Learning, 8),
            revlog(RevlogReviewKind::Review, 6),
        ];
        // | = Ignore before
        // L = learning step
        // L | L R
        assert_eq!(convert_ignore_before(revlogs, true, days_ago_ms(9)), None);
    }

    #[test]
    fn skip_initial_relearning_steps() {
        let revlogs = &[
            revlog(RevlogReviewKind::Review, 10),
            RevlogEntry {
                button_chosen: 1, // Again
                interval: -600,
                ..revlog(RevlogReviewKind::Review, 8)
            },
            revlog(RevlogReviewKind::Relearning, 8),
            revlog(RevlogReviewKind::Review, 6),
        ];
        // | = Ignore before
        // A = Again
        // X = Relearning
        // R | A X R
        assert_eq!(
            convert_ignore_before(revlogs, false, days_ago_ms(9)),
            fsrs_items!([review(0), review(2)])
        );
    }

    #[test]
    fn ignore_before_date_between_learning_steps_when_reviewing() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 10),
            revlog(RevlogReviewKind::Learning, 8),
            revlog(RevlogReviewKind::Review, 2),
        ];
        // L | L R
        assert_ne!(
            convert_ignore_before(revlogs, false, days_ago_ms(9)),
            convert(revlogs, false)
        );
        assert_eq!(
            convert_ignore_before(revlogs, false, days_ago_ms(9))
                .unwrap()
                .last()
                .unwrap()
                .reviews
                .len(),
            2
        );
        // | L L R
        assert_eq!(
            convert_ignore_before(revlogs, false, days_ago_ms(11)),
            convert(revlogs, false)
        );
    }

    #[test]
    fn handle_ignore_before_when_no_learning_steps() {
        let revlogs = &[
            revlog(RevlogReviewKind::Review, 10),
            revlog(RevlogReviewKind::Review, 8),
            revlog(RevlogReviewKind::Review, 6),
        ];
        // R | R R
        assert_eq!(
            convert_ignore_before(revlogs, false, days_ago_ms(9))
                .unwrap()
                .last()
                .unwrap()
                .reviews
                .len(),
            2
        );
    }

    #[test]
    fn ignore_before_after_last_revlog_entry() {
        let revlogs = &[
            revlog(RevlogReviewKind::Learning, 10),
            revlog(RevlogReviewKind::Review, 6),
        ];
        // L R |
        assert_eq!(convert_ignore_before(revlogs, false, days_ago_ms(4)), None);
    }

    #[test]
    fn training_search_uses_override_when_non_empty() {
        assert_eq!(
            super::training_search("deck:train", Some("deck:eval")),
            "deck:eval"
        );
        assert_eq!(
            super::training_search("deck:train", Some("   deck:eval2  ")),
            "deck:eval2"
        );
    }

    #[test]
    fn training_search_falls_back_to_search_when_empty() {
        assert_eq!(super::training_search("deck:train", None), "deck:train");
        assert_eq!(
            super::training_search("deck:train", Some("   ")),
            "deck:train"
        );
    }

    #[test]
    fn external_evaluation_is_enabled_only_for_different_searches() {
        assert!(super::uses_external_evaluation(
            "preset:vocabulary rated:1",
            "preset:vocabulary"
        ));
        assert!(!super::uses_external_evaluation(
            "preset:vocabulary",
            "preset:vocabulary"
        ));
    }

    #[test]
    fn external_target_evaluation_rejects_empty_sets() {
        let err = super::evaluate_from_training_to_external_targets(
            ComputeParametersInput {
                training_config: None,
                train_set: vec![],
                card_ids: None,
                progress: None,
                enable_short_term: true,
                enable_sched_penalties: true,
                model_version: ComputeParametersVersion::Fsrs6,
                num_relearning_steps: Some(1),
            },
            vec![FSRSItem {
                reviews: vec![review(0), review(2)],
            }],
        )
        .unwrap_err();
        assert!(matches!(
            err,
            AnkiError::FsrsInsufficientData | AnkiError::FsrsInsufficientReviews { .. }
        ));
    }

    #[test]
    fn external_target_evaluation_rejects_empty_evaluation_set() {
        let err = super::evaluate_from_training_to_external_targets(
            ComputeParametersInput {
                training_config: None,
                train_set: vec![FSRSItem {
                    reviews: vec![review(0), review(2)],
                }],
                card_ids: None,
                progress: None,
                enable_short_term: true,
                enable_sched_penalties: true,
                model_version: ComputeParametersVersion::Fsrs6,
                num_relearning_steps: Some(1),
            },
            vec![],
        )
        .unwrap_err();
        assert!(matches!(
            err,
            AnkiError::FsrsInsufficientData | AnkiError::FsrsInsufficientReviews { .. }
        ));
    }
}
