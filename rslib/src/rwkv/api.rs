// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
use prost::Message;

use crate::deckconfig::DeckConfig;
use crate::deckconfig::DeckConfigId;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::decks::NativeDeckName;

#[derive(Debug, Clone)]
pub struct ReviewInput {
    pub card_id: i64,
    pub note_id: Option<i64>,
    pub deck_id: Option<i64>,
    pub preset_id: Option<i64>,
    pub is_query: bool,
    pub ease: Option<u8>,
    pub duration_millis: Option<i64>,
    /// Legacy field name: answered inputs contain the training dataset state.
    pub card_type: Option<i64>,
    pub day_offset: Option<i64>,
    pub current_elapsed_days: Option<i64>,
    pub current_elapsed_seconds: Option<i64>,
    pub target_retentions: [Option<f32>; 4],
    pub enforce_grade_order: bool,
}

pub struct ReviewState<'a> {
    pub card: Option<&'a [u8]>,
    pub deck: Option<&'a [u8]>,
    pub note: Option<&'a [u8]>,
    pub preset: Option<&'a [u8]>,
    pub global: Option<&'a [u8]>,
}

pub struct ReviewOutput {
    pub retrievability: f32,
    pub curve_retrievability: Option<f32>,
    pub button_probabilities: [f32; 4],
    pub current_interval: Option<u32>,
    pub current_s90: Option<f32>,
    pub intervals: [Option<f32>; 4],
    pub s90s: [Option<f32>; 4],
    pub card_state: Vec<u8>,
    pub deck_state: Vec<u8>,
    pub note_state: Vec<u8>,
    pub preset_state: Vec<u8>,
    pub global_state: Vec<u8>,
}

pub struct ReviewPredictionRequest {
    pub input: ReviewInput,
    pub state: ReviewStateOwned,
}

pub struct ReviewStateOwned {
    pub card: Option<Vec<u8>>,
    pub deck: Option<Vec<u8>>,
    pub note: Option<Vec<u8>>,
    pub preset: Option<Vec<u8>>,
    pub global: Option<Vec<u8>>,
}

pub struct ReviewPredictionOutput {
    pub retrievability: f32,
    pub curve_retrievability: Option<f32>,
    pub button_probabilities: [f32; 4],
    pub current_interval: Option<u32>,
    pub current_s90: Option<f32>,
    pub intervals: [Option<f32>; 4],
    pub s90s: [Option<f32>; 4],
}

pub struct RwkvWarmUpSnapshot {
    pub card_states: Vec<(i64, Vec<u8>)>,
    pub note_states: Vec<(i64, Vec<u8>)>,
    pub deck_states: Vec<(i64, Vec<u8>)>,
    pub preset_states: Vec<(i64, Vec<u8>)>,
    pub global_state: Option<Vec<u8>>,
}

pub struct RwkvWorkloadSimulationSnapshot {
    pub card_states: Vec<(i64, Vec<u8>)>,
    pub note_states: Vec<(i64, Vec<u8>)>,
    pub deck_states: Vec<(i64, Vec<u8>)>,
    pub preset_states: Vec<(i64, Vec<u8>)>,
    pub global_state: Option<Vec<u8>>,
    pub runtime_state: Option<Vec<u8>>,
}

#[derive(Clone)]
pub struct RwkvWorkloadSimulationInput {
    pub review_input: ReviewInput,
    pub interval_days: Option<i64>,
    pub ease_factor: Option<i64>,
    pub reps: Option<i64>,
    pub lapses: Option<i64>,
}

pub struct RwkvWorkloadSimulationPoint {
    pub memorized: f32,
    pub weighted_memorized: f32,
    pub cost: f32,
    pub review_count: u32,
}

pub struct RwkvWorkloadSimulationOutput {
    pub reviewless_end_memorized: f32,
    pub reviewless_end_weighted_memorized: f32,
    pub points: Vec<(u32, RwkvWorkloadSimulationPoint)>,
}

pub struct RwkvWorkloadReviewModel {
    pub grade_seconds: [f32; 4],
    pub bucket_probabilities: Vec<(u32, [f32; 4])>,
}

pub struct RwkvWorkloadSimulationConfig {
    pub min_dr: u32,
    pub max_dr: u32,
    pub target_dr_step: u32,
    pub days_to_simulate: u32,
    pub review_limit: u32,
    pub new_limit: u32,
    pub new_cards_ignore_review_limit: bool,
    pub max_interval: u32,
    pub review_order: ReviewCardOrder,
    pub suspend_after_lapses: Option<u32>,
    pub state_update_interval: u32,
    pub review_model: RwkvWorkloadReviewModel,
}

#[derive(Clone)]
pub struct RwkvComparisonCard {
    pub input: RwkvWorkloadSimulationInput,
    pub due_day: i64,
    pub fsrs_memory: Option<(f32, f32, f32)>,
    pub fsrs_parameters: Vec<f32>,
    pub historical_retention: f32,
}

/// One recorded background answer, placed in a repeating 365-day pattern.
#[derive(Clone)]
pub struct RwkvComparisonBackgroundReview {
    pub input: ReviewInput,
    pub pattern_day: u32,
    pub second_of_day: u32,
    pub last_review_day: i64,
    pub last_review_second: i64,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RwkvComparisonLearner {
    Fsrs,
    Rwkv,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RwkvPerformanceScenario {
    Steady,
    TemporaryDrop,
    PersistentDrop,
    TemporaryImprovement,
}

pub struct RwkvComparisonConfig {
    pub min_dr: u32,
    pub max_dr: u32,
    pub target_dr_step: u32,
    pub days_to_simulate: u32,
    pub review_limit: f32,
    pub new_limit: f32,
    pub new_cards_ignore_review_limit: bool,
    pub max_interval: u32,
    pub review_order: ReviewCardOrder,
    pub suspend_after_lapses: Option<u32>,
    pub learner: RwkvComparisonLearner,
    pub scenario: RwkvPerformanceScenario,
    pub repetitions: u32,
    pub output_scale: f32,
    pub review_model: RwkvWorkloadReviewModel,
    pub background: Vec<RwkvComparisonBackgroundReview>,
}

pub struct RwkvComparisonOutput {
    pub fsrs_scored_by_fsrs: RwkvWorkloadSimulationOutput,
    pub rwkv_scored_by_fsrs: RwkvWorkloadSimulationOutput,
    pub fsrs_scored_by_rwkv: RwkvWorkloadSimulationOutput,
    pub rwkv_scored_by_rwkv: RwkvWorkloadSimulationOutput,
    pub estimated_fsrs_states: u32,
}

/// Which scheduler sets the next interval of a forecast card. Grades are
/// always drawn from the RWKV curve model's recall probability.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RwkvForecastScheduler {
    /// RWKV-Curve (and RWKV-Instant) presets: curve-model intervals at the
    /// card's desired retention.
    Rwkv,
    /// FSRS presets: FSRS memory state and parameters at the card's desired
    /// retention.
    Fsrs,
    /// SM-2 presets: ease-factor intervals (no fuzz, no interval modifiers).
    Sm2,
}

#[derive(Clone)]
pub struct RwkvForecastCard {
    pub input: RwkvWorkloadSimulationInput,
    /// Days from today until the card is due; <= 0 means due today. `None`
    /// derives the due day from the curve, as the DR workload simulation does.
    pub due_day: Option<i64>,
    /// Limit group (usually the deck id); each group has its own limits.
    pub group: i64,
    pub desired_retention: f32,
    pub scheduler: RwkvForecastScheduler,
    pub fsrs_memory: Option<(f32, f32, f32)>,
    pub fsrs_parameters: Vec<f32>,
    pub historical_retention: f32,
}

#[derive(Clone, Debug)]
pub struct RwkvForecastGroup {
    pub group: i64,
    pub review_limit: u32,
    pub new_limit: u32,
    /// Already done today: day 0 only gets the rest of each limit.
    pub reviews_done_today: u32,
    pub new_done_today: u32,
}

pub struct RwkvWorkloadForecastConfig {
    pub days: u32,
    pub paths: u32,
    pub seed: u32,
    pub groups: Vec<RwkvForecastGroup>,
    /// Limits of groups missing from `groups`.
    pub default_review_limit: u32,
    pub default_new_limit: u32,
    pub new_cards_ignore_review_limit: bool,
    pub max_interval: u32,
    pub review_order: ReviewCardOrder,
    pub suspend_after_lapses: Option<u32>,
    pub state_update_interval: u32,
    /// Approximate mode (with `state_update_interval` 1): RWKV intervals come
    /// from the selected grade's curve alone, without running the other three
    /// grades for the grade-order check (one answer step instead of four).
    pub single_step_answers: bool,
    pub review_model: RwkvWorkloadReviewModel,
}

/// A review card in the Instant forecast. The stored due day is an ordering
/// key; eligibility comes from the current Instant prediction instead.
#[derive(Clone)]
pub struct RwkvInstantForecastCard {
    pub input: RwkvWorkloadSimulationInput,
    pub due_day: i64,
    pub group: i64,
    pub desired_retention: f32,
    pub instant_enabled: bool,
    /// Number of answers in the studied deck since this card's last answer.
    pub intervening_reviews: Option<u32>,
    /// The latest real answer's kind and grade, when it happened today.
    pub last_kind_and_grade: Option<(i64, i64)>,
    pub queue: i32,
    /// Due time for a learning card, relative to today's rollover.
    pub learning_due_millis: i64,
    pub remaining_steps: u32,
    pub new_position: u32,
    pub fsrs_memory: Option<(f32, f32, f32)>,
    pub fsrs_parameters: Vec<f32>,
    pub fsrs_historical_retention: f32,
    pub fsrs_enabled: bool,
}

/// Read-only copies of Anki's deck and preset settings for queue simulation.
pub fn decode_instant_forecast_decks(
    decks: Vec<Vec<u8>>,
    configs: Vec<Vec<u8>>,
) -> std::io::Result<(Vec<Deck>, Vec<DeckConfig>)> {
    let invalid = |error: String| std::io::Error::new(std::io::ErrorKind::InvalidInput, error);
    let mut decks = decks
        .into_iter()
        .map(|bytes| {
            let value = anki_proto::decks::Deck::decode(bytes.as_slice())
                .map_err(|error| invalid(error.to_string()))?;
            Deck::try_from(value).map_err(|error| invalid(error.to_string()))
        })
        .collect::<std::io::Result<Vec<_>>>()?;
    let mut configs = configs
        .into_iter()
        .map(|bytes| {
            anki_proto::deck_config::DeckConfig::decode(bytes.as_slice())
                .map(DeckConfig::from)
                .map_err(|error| invalid(error.to_string()))
        })
        .collect::<std::io::Result<Vec<_>>>()?;
    decks.sort_by(|a, b| a.name.as_native_str().cmp(b.name.as_native_str()));
    if let Some(first) = decks.first() {
        let root_name = first.name.as_native_str();
        let is_tree = decks.iter().all(|deck| {
            let name = deck.name.as_native_str();
            name == root_name
                || name
                    .strip_prefix(root_name)
                    .is_some_and(|suffix| suffix.starts_with('\u{1f}'))
        });
        if !is_tree {
            // LimitTreeMap represents one tree. An unlimited virtual root
            // lets a search spanning separate trees retain each real root's
            // limits without imposing one deck's limits on another.
            let mut name = "__rwkv_forecast_root__".to_owned();
            while decks.iter().any(|deck| deck.name.as_native_str() == name) {
                name.push('_');
            }
            let mut root = Deck::new_normal();
            root.id = DeckId(i64::MIN);
            root.name = NativeDeckName::from_human_name(&name);
            root.normal_mut().expect("new deck is normal").config_id = i64::MIN;
            let mut root_config = DeckConfig {
                id: DeckConfigId(i64::MIN),
                ..Default::default()
            };
            root_config.inner.reviews_per_day = i32::MAX as u32;
            root_config.inner.new_per_day = i32::MAX as u32;
            root_config.inner.rwkv_review_minimum_reviews_per_day = 0;
            decks.insert(0, root);
            configs.push(root_config);
        }
    }
    Ok((decks, configs))
}

/// One answer grade's response-time regression against historical Instant R.
#[derive(Clone, Copy)]
pub struct RwkvInstantReviewTimeGrade {
    pub intercept: f32,
    pub slope: f32,
    pub min_recall: f32,
    pub max_recall: f32,
    pub fallback_seconds: f32,
}

pub struct RwkvInstantReviewTimeModel {
    /// Mean of per-day mean durations in the sampled historical pool.
    pub reference_seconds: f32,
    pub grades: [Option<RwkvInstantReviewTimeGrade>; 4],
}

impl RwkvInstantReviewTimeModel {
    pub(crate) fn valid(&self) -> bool {
        self.reference_seconds.is_finite()
            && self.reference_seconds > 0.0
            && self.grades.iter().flatten().all(|grade| {
                grade.intercept.is_finite()
                    && grade.slope.is_finite()
                    && grade.min_recall.is_finite()
                    && grade.max_recall.is_finite()
                    && (0.0..=1.0).contains(&grade.min_recall)
                    && (grade.min_recall..=1.0).contains(&grade.max_recall)
                    && grade.fallback_seconds.is_finite()
                    && grade.fallback_seconds > 0.0
            })
    }

    pub(crate) fn duration_millis(&self, sampled: u32, recall: f32, ease: u8) -> u32 {
        let Some(grade) = ease
            .checked_sub(1)
            .and_then(|index| self.grades.get(index as usize))
            .and_then(|grade| *grade)
        else {
            return sampled;
        };
        if !recall.is_finite() {
            return sampled;
        }
        let recall = recall.clamp(grade.min_recall, grade.max_recall);
        let predicted = grade.intercept + grade.slope * (1.0 - recall);
        let seconds = if predicted.is_finite() && predicted > 0.0 {
            predicted
        } else {
            grade.fallback_seconds
        };
        (f64::from(sampled) * f64::from(seconds) / f64::from(self.reference_seconds))
            .round()
            .min(f64::from(u32::MAX)) as u32
    }
}

pub struct RwkvInstantForecastConfig {
    pub days: u32,
    pub paths: u32,
    /// When present, run three matched paths with recall offsets -sigma, 0,
    /// +sigma.
    pub scenario_sigma: Option<f32>,
    /// Select three approximate strata of answer outcomes using actual recall.
    pub stratified_answers: bool,
    /// Historical adjustment to the modeled answer-outcome dispersion.
    pub stratified_scale: f32,
    /// Root-mean-square systematic error of the daily recall rate.
    pub stratified_bias_sd: f32,
    /// Modeled answer variance per answer, used to size the bias in answers.
    pub stratified_review_variance: f32,
    pub report_days: bool,
    pub seed: u32,
    pub decks: Vec<Deck>,
    pub deck_configs: Vec<DeckConfig>,
    pub study_deck_id: i64,
    pub today: u32,
    pub new_cards_ignore_review_limit: bool,
    pub fsrs_learning_queues_disabled: bool,
    pub fsrs_short_term_with_steps_enabled: bool,
    pub learn_ahead_secs: u32,
    /// Recorded answers as (milliseconds after scheduler rollover, answer ms).
    pub timing_patterns: Vec<Vec<(u32, u32)>>,
    pub timing_pattern_success_shares: Vec<[f32; 4]>,
    pub review_time_model: Option<RwkvInstantReviewTimeModel>,
    pub now_millis: u32,
    pub same_day_allowed: bool,
    pub min_intervening_reviews: u32,
    pub min_elapsed_secs: u32,
    pub refresh_interval: u32,
    /// Forecast-only speed/fidelity trade-off: when greater than
    /// `refresh_interval`, queued scores are refreshed only every this many
    /// answers instead. 0 keeps the configured refresh interval.
    pub approximate_refresh_answers: u32,
    pub candidate_refresh_enabled: bool,
    pub candidate_refresh_limit: usize,
    pub precompute_next_card: bool,
    pub review_order: ReviewCardOrder,
    pub review_model: RwkvWorkloadReviewModel,
}

/// Distribution of one daily quantity over the simulated paths.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct RwkvForecastBand {
    pub mean: f32,
    pub p1: f32,
    pub p5: f32,
    pub p50: f32,
    pub p95: f32,
    pub p99: f32,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct RwkvForecastDay {
    pub day: u32,
    /// Review cards due that day (before limits, including backlog).
    pub due: RwkvForecastBand,
    /// Review cards answered that day (after limits).
    pub done: RwkvForecastBand,
    /// New cards introduced that day.
    pub new: RwkvForecastBand,
    /// Review answers graded Again that day.
    pub lapses: RwkvForecastBand,
    /// Estimated answer time of all of that day's answers, in seconds.
    pub seconds: RwkvForecastBand,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct RwkvForecastGroupDays {
    pub group: i64,
    pub due_mean: Vec<f32>,
    pub done_mean: Vec<f32>,
    pub new_mean: Vec<f32>,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct RwkvWorkloadForecastOutput {
    pub paths: u32,
    pub days: Vec<RwkvForecastDay>,
    pub groups: Vec<RwkvForecastGroupDays>,
    pub estimated_fsrs_states: u32,
    /// Scenario order is R-sigma, R, R+sigma; each day contains due, done,
    /// new, lapses and seconds. Ordinary stochastic forecasts leave this empty.
    pub scenarios: Option<Vec<Vec<[f32; 5]>>>,
}
