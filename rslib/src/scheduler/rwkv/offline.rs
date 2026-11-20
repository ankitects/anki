// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! RWKV-Instant without the desktop's Python.
//!
//! The desktop drives `crate::rwkv::RwkvInference` from
//! `qt/aqt/rwkv_scheduler.py`. Clients that have no Python (AnkiDroid) call
//! `RwkvPrepareOffline` once the collection is open; from then on the
//! collection keeps the model state up to date with the revlog and installs the
//! same transient score maps the desktop installs, at the two places that
//! consume them: before a review queue is built, and before deck counts are
//! computed.
//!
//! Nothing here writes to cards. It records answers with the same revlog kind
//! as the desktop, including filtered decks and same-day repeats. If scoring
//! fails, the installed scores are cleared and the standard scheduler applies.
//!
//! The model state is also kept in a file next to the collection
//! (`offline_state.rs`): a new start loads it and replays only the reviews
//! that came after it, instead of the whole history.

use std::fmt;
use std::hash::Hasher;
use std::path::Path;
use std::path::PathBuf;
use std::sync::atomic::AtomicU64;
use std::sync::atomic::Ordering;
use std::sync::Mutex;
use std::thread::JoinHandle;
use std::time::Instant;

use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
use anki_proto::scheduler::rwkv_historical_review_inputs_response::Review;
use anki_proto::scheduler::rwkv_offline_forecast_response::Card as ForecastCard;
use anki_proto::scheduler::rwkv_review_input_rows_for_cards_response::Row;
use anki_proto::scheduler::RwkvHistoricalReviewIdentity;
use anki_proto::scheduler::RwkvOfflineCalibrationResponse;
use anki_proto::scheduler::RwkvOfflineForecastRequest;
use anki_proto::scheduler::RwkvOfflineForecastResponse;
use anki_proto::scheduler::RwkvOfflineInstantPassProgress;
use anki_proto::scheduler::RwkvOfflineInstantPassStepRequest;
use anki_proto::scheduler::RwkvPrepareOfflineRequest;
use anki_proto::scheduler::RwkvPrepareOfflineResponse;
use anki_proto::scheduler::RwkvReviewInputRowsForCardsRequest;
use prost::Message;
use rusqlite::OptionalExtension;

use super::history::RWKV_COUNTED_REVIEW;
use super::offline_engine::Engine;
use super::offline_state;
use super::*;
use crate::collection::RwkvReviewQueueScoreEntry;
use crate::decks::limits::LimitTreeMap;
use crate::rwkv::ReviewInput;
use crate::rwkv::StatePrecision;
use crate::scheduler::answering::CardAnswer;
use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;
use crate::scheduler::states::CardState;
use crate::scheduler::states::FilteredState;
use crate::scheduler::states::NormalState;
use crate::storage::RWKV_HISTORY_RESET_AFTER_SQL;

#[cfg(test)]
#[path = "offline_review_kind_tests.rs"]
mod review_kind_tests;
#[path = "offline_workload.rs"]
mod workload;

/// The desktop always loads the model with these (they only affect interval
/// outputs, which Instant does not use).
const MODEL_TARGET_RETENTION: f32 = 0.9;
const MODEL_MAX_INTERVAL_DAYS: u32 = 36_500;
const DEFAULT_TARGET_RETENTION: f32 = 0.9;
/// Scores depend on the seconds since each card's last review, so they are
/// recomputed once they are this old, even when no review happened.
const QUEUE_SCORE_MAX_AGE_SECS: i64 = 30;
/// Deck counts past their max age re-score only the cards within this of their
/// target: recall drifts slowly with the clock (203k-review collection: at most
/// 0.37 points in 30 minutes, no card crossed its target in 2 minutes), and
/// scoring all of a 10,870-card deck took 0.6 s on a Tab S7+ every 2 minutes.
const NEAR_TARGET: f32 = 0.02;
/// Cards answered less than this ago are re-scored too: their recall still
/// moves fast (one moved 21 points in 2 minutes).
const RECENT_ANSWER_SECS: u32 = 24 * 60 * 60;
/// ...and every scoreable card again after this long, a new day, or any change
/// to the model state: an answer also moves the deck, preset and collection
/// states every card's recall reads (10 answers moved that deck's cards 8
/// points on average; 4,148 of 10,903 crossed their target).
const FULL_SCORE_MAX_AGE_SECS: i64 = 30 * 60;
const DECK_COUNT_SCORE_MAX_AGE_SECS: i64 = 120;
/// The desktop's default and bounds for `rwkv_review_batch_size`, the number of
/// cards a "faster, approximate" queue update rechecks.
const DEFAULT_REVIEW_BATCH_SIZE: u32 = 512;
const MIN_REVIEW_BATCH_SIZE: u32 = 64;
const MAX_REVIEW_BATCH_SIZE: u32 = 8192;
/// The state file is rewritten after a full replay, and otherwise once this
/// many reviews were absorbed since it was written (a start replays at most
/// these few).
const SAVE_STATE_AFTER_REVIEWS: u64 = 200;
/// Reviews given to one bulk replay call. The bulk replay holds features and
/// activations for every review it is given (~1 KB each): a 1M-review history
/// in one call peaked 1.9 GB above the states, 3.7M didn't fit on a Pixel 8a.
const REPLAY_CHUNK_REVIEWS: usize = 65_536;
/// The forecast's default cards: review cards that RWKV-Instant schedules
/// (learning cards follow their steps), as the desktop add-on.
const FORECAST_SEARCH: &str = "is:review -is:learn -is:suspended -is:buried";

/// The model path of the last successful `RwkvPrepareOffline`. The runtime
/// lives in the collection state, which is lost when the collection is
/// reopened; this lets the next queue build or deck count restore it.
/// The model file and the precision its card/note states are kept in (the
/// app's Settings > kuma3; int8 by default, as in the fork's desktop).
#[derive(Clone, Debug, PartialEq)]
pub(super) struct OfflineModel {
    pub(super) path: PathBuf,
    pub(super) precision: StatePrecision,
}

#[derive(Clone, Debug, PartialEq)]
pub(super) struct SelectedOfflineModel {
    pub(super) model: OfflineModel,
    pub(super) leak_free_inputs: bool,
    /// With RWKV-Instant and RWKV-Curve both in use: the size's Instant model,
    /// run beside `model` (its two-head model).
    pub(super) instant_path: Option<PathBuf>,
    /// `model` is the size's Instant model alone (RWKV-Instant without
    /// RWKV-Curve): no curve, so no workload simulation.
    pub(super) instant_only: bool,
}

impl From<OfflineModel> for SelectedOfflineModel {
    fn from(model: OfflineModel) -> Self {
        Self {
            model,
            leak_free_inputs: false,
            instant_path: None,
            instant_only: false,
        }
    }
}

static REGISTERED_MODEL: Mutex<Option<SelectedOfflineModel>> = Mutex::new(None);

fn registered_model() -> Option<SelectedOfflineModel> {
    REGISTERED_MODEL.lock().unwrap().clone()
}

/// The replay under way (reviews done, of total; total 0: none), for the
/// app's progress bar through the status call.
static REPLAY_DONE: AtomicU64 = AtomicU64::new(0);
static REPLAY_TOTAL: AtomicU64 = AtomicU64::new(0);

/// The runtime being built off the collection lock, and the collection path
/// it is for (see `rwkv_offline_start_build`).
#[allow(clippy::type_complexity)]
static BUILDING: Mutex<Option<(PathBuf, JoinHandle<Result<Box<RwkvOfflineRuntime>>>)>> =
    Mutex::new(None);

/// Reviews to replay to bring a model state in line with the revlog.
struct HistoryPlan {
    /// Start from the freshly loaded model instead of the current state.
    reset: bool,
    /// In chunks of [REPLAY_CHUNK_REVIEWS], each freed once replayed: one list
    /// held ~0.6 GB for the whole replay of a 3.7M-review history.
    reviews: Vec<Vec<ReviewInput>>,
    /// Their revlog ids, in the same order (for [Calibration]).
    review_ids: Vec<i64>,
    /// The history identity once replayed.
    identity: RwkvHistoricalReviewIdentity,
    collection_mod: TimestampMillis,
    /// What the history was read under ([ReplayStamp]), if known.
    stamp: Option<ReplayStamp>,
}

/// A cheap summary of everything the history replay reads besides the
/// reviews' own fields: when it is unchanged after a change to the collection
/// (deck options, a note edit), the history is too, and the fingerprint pass
/// over every review (~20 s for 3.7M reviews on a Pixel 8a, holding the
/// collection) is skipped. None when the collection has preset overlay rules,
/// which match cards by search: then the full check runs.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
struct ReplayStamp {
    revlog_rows: u64,
    last_review_id: i64,
    /// Cards (id, note, home deck, new status and reset boundary), each
    /// deck's options group, the day boundary and dynamic preset setting.
    rest: u64,
}

impl ReplayStamp {
    /// The stamp after one answer was added (an answer moves no card).
    fn appended(self, review_id: i64) -> Self {
        Self {
            revlog_rows: self.revlog_rows + 1,
            last_review_id: self.last_review_id.max(review_id),
            ..self
        }
    }
}

pub(crate) struct RwkvOfflineRuntime {
    model: SelectedOfflineModel,
    inference: Engine,
    /// Feature/curve state of the freshly loaded model, for full rebuilds.
    initial_cache_state: Vec<u8>,
    /// Identity of the review history the model state has absorbed.
    identity: Option<RwkvHistoricalReviewIdentity>,
    /// Collection modification time when the history was last checked.
    checked_at_mod: Option<TimestampMillis>,
    /// The [ReplayStamp] the history was last checked under (with an identity).
    checked_stamp: Option<ReplayStamp>,
    /// Bumped whenever the model state changes; invalidates cached scores.
    generation: u64,
    scopes: HashMap<DeckId, ScopeScores>,
    /// Review count of the state in the state file, if it holds this one's.
    saved_review_count: Option<u64>,
    /// The state file being written ([Self::save_state]).
    saving: Option<std::thread::JoinHandle<()>>,
    /// The S90s of the RWKV-Curve answers last given to each card
    /// ([Collection::rwkv_offline_curve]), for its answer: a queue fetch
    /// gives the buttons of more than the card answered next.
    curve_s90: HashMap<CardId, [Option<f32>; 4]>,
    /// Once asked for ([Collection::rwkv_offline_calibration]); kept up to
    /// date by every later replay and saved with the state.
    calibration: Option<Calibration>,
}

/// The recall the model predicted just before each answer, for the stats
/// page's calibration graphs. Only the newest 30% of the history at the full
/// replay that started it, plus every answer since: the desktop scores that
/// part (its test fold without FSRS validation folds), and recording costs a
/// query pass per review (a recorded replay of 3.5M reviews took 2.6x a plain
/// one on the PC). With `all`, the whole history: once FSRS validation folds
/// exist, the desktop scores RWKV on their reviews (most of the history).
#[derive(Default)]
struct Calibration {
    /// Recording the whole history (`from` is 0).
    all: bool,
    /// The first history position recorded.
    from: u64,
    /// (review id, prediction), oldest first.
    predictions: Vec<(i64, f32)>,
}

impl Drop for RwkvOfflineRuntime {
    fn drop(&mut self) {
        self.finish_saving();
    }
}

impl fmt::Debug for RwkvOfflineRuntime {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("RwkvOfflineRuntime")
            .field("model", &self.model)
            .field("identity", &self.identity)
            .field("generation", &self.generation)
            .finish_non_exhaustive()
    }
}

struct ScopeScores {
    generation: u64,
    days_elapsed: u32,
    computed_at: TimestampSecs,
    /// When every card in the scope was last scored ([NEAR_TARGET]), and the
    /// model state's generation then (a queue update in approximate mode marks
    /// the scope current without scoring every card).
    fully_computed_at: TimestampSecs,
    fully_computed_generation: u64,
    cards: Vec<ScoredCard>,
    /// Cards answered since the last update of the whole scope (or of its
    /// candidates): their scores are older than their answer.
    answered: Vec<CardId>,
    /// Check for newly eligible reviews when reviews or the whole queue run
    /// out.
    force_refresh: bool,
}

#[cfg(test)]
#[path = "offline_preview_s90_tests.rs"]
mod preview_s90_tests;

#[derive(Clone, Copy)]
struct ScoredCard {
    card_id: CardId,
    /// The deck the card is in (filtered-deck residents are never scored).
    deck_id: DeckId,
    retrievability: f32,
    target_retention: Option<f32>,
}

impl RwkvOfflineRuntime {
    fn load(model: impl Into<SelectedOfflineModel>) -> Result<Self> {
        let model = model.into();
        let inference = match &model.instant_path {
            Some(instant) => Engine::load_pair(
                model.model.path.clone(),
                instant.clone(),
                MODEL_TARGET_RETENTION,
                MODEL_MAX_INTERVAL_DAYS,
                model.model.precision,
                model.leak_free_inputs,
            )?,
            None => Engine::load_with_precision(
                model.model.path.clone(),
                MODEL_TARGET_RETENTION,
                MODEL_MAX_INTERVAL_DAYS,
                model.model.precision,
                model.leak_free_inputs,
            )?,
        };
        let initial_cache_state = inference.cache_state();
        Ok(Self {
            model,
            inference,
            initial_cache_state,
            identity: None,
            checked_at_mod: None,
            checked_stamp: None,
            generation: 0,
            scopes: HashMap::new(),
            saved_review_count: None,
            saving: None,
            curve_s90: HashMap::new(),
            calibration: None,
        })
    }

    /// A freshly loaded runtime, with the state from the state file at `path`
    /// when there is a usable one. Whether that state still fits the
    /// collection is checked by the next history sync, which replays only
    /// what is missing (or everything, when the history changed otherwise).
    fn load_with_saved_state(model: SelectedOfflineModel, path: &Path) -> Result<Self> {
        let mut runtime = Self::load(model)?;
        if path.exists() {
            if let Err(err) = runtime.restore_saved_state(path) {
                tracing::warn!(?err, "saved RWKV offline state not used");
                runtime.reset()?;
            }
        }
        Ok(runtime)
    }

    fn restore_saved_state(&mut self, path: &Path) -> std::io::Result<()> {
        let header = offline_state::header(&self.model)?;
        let identity = offline_state::read(path, &header, &mut self.inference)?;
        let identity = RwkvHistoricalReviewIdentity::decode(identity.as_slice())
            .map_err(std::io::Error::other)?;
        self.saved_review_count = Some(identity.review_count);
        self.calibration = offline_state::read_calibration(
            &calibration_path(path),
            &header,
            &identity.encode_to_vec(),
        )
        .ok()
        .map(|(from, predictions)| Calibration {
            all: from == 0,
            from,
            predictions,
        });
        self.identity = Some(identity);
        self.checked_at_mod = None;
        self.state_changed();
        Ok(())
    }

    /// Writes the current state to the state file at `path`, on a thread of
    /// its own: this runs inside an answer (every 200 of them), and writing a
    /// big collection's file (hundreds of MB) would hold up the next card.
    /// Only the snapshot is taken here, which copies pointers. A failure only
    /// means the next start replays more.
    fn save_state(&mut self, path: &Path) {
        let Some(identity) = self.identity.clone() else {
            return;
        };
        let header = match offline_state::header(&self.model) {
            Ok(header) => header,
            Err(err) => return tracing::warn!(?err, "RWKV offline state not saved"),
        };
        let state = self.inference.state_snapshot();
        let calibration = self
            .calibration
            .as_ref()
            .map(|calibration| (calibration.from, calibration.predictions.clone()));
        let path = path.to_owned();
        self.saved_review_count = Some(identity.review_count);
        // the previous save first, so the newest state is the one left
        self.finish_saving();
        self.saving = Some(std::thread::spawn(move || {
            let identity = identity.encode_to_vec();
            if let Err(err) = offline_state::write(&path, &header, &identity, &state) {
                tracing::warn!(?err, "RWKV offline state not saved");
            }
            if let Some((from, predictions)) = calibration {
                if let Err(err) = offline_state::write_calibration(
                    &calibration_path(&path),
                    &header,
                    &identity,
                    from,
                    &predictions,
                ) {
                    tracing::warn!(?err, "RWKV calibration not saved");
                }
            }
        }));
    }

    /// Waits for a save still being written (also when the runtime is dropped,
    /// so closing the collection or ending a host tool doesn't lose it).
    fn finish_saving(&mut self) {
        if let Some(saving) = self.saving.take() {
            let _ = saving.join();
        }
    }

    /// Replays what `plan` says (the CPU part of a history sync; it needs no
    /// collection). Returns the number of reviews replayed.
    fn apply_history_plan(&mut self, plan: HistoryPlan, state_path: &Path) -> Result<u64> {
        let replayed = plan.reviews.iter().map(Vec::len).sum::<usize>() as u64;
        if plan.reset {
            self.reset()?;
            if let Some(calibration) = &mut self.calibration {
                let all = calibration.all;
                *calibration = Calibration {
                    all,
                    from: if all { 0 } else { replayed * 7 / 10 },
                    predictions: Vec::new(),
                };
            }
        }
        // the history position of the next review replayed
        let mut position = self.identity.as_ref().map_or(0, |id| id.review_count);
        let mut review_ids = plan.review_ids.into_iter();
        // only a partial replay keeps scores (below)
        let answered: Vec<_> = if plan.reset {
            Vec::new()
        } else {
            plan.reviews
                .iter()
                .flatten()
                .map(|r| CardId(r.card_id))
                .collect()
        };
        // The identity is cleared first so a failed replay forces a rebuild.
        self.identity = None;
        REPLAY_DONE.store(0, Ordering::Relaxed);
        REPLAY_TOTAL.store(replayed, Ordering::Relaxed);
        let result = (|| {
            for chunk in plan.reviews {
                let len = chunk.len() as u64;
                let ids: Vec<i64> = review_ids.by_ref().take(chunk.len()).collect();
                let record = self
                    .calibration
                    .as_ref()
                    .is_some_and(|calibration| position + len > calibration.from);
                let predictions = self.inference.warm_up_reviews(chunk, record)?;
                if let Some(calibration) = &mut self.calibration {
                    for (index, prediction) in predictions {
                        if position + index as u64 >= calibration.from {
                            if let Some(prediction) = valid_probability(prediction) {
                                calibration.predictions.push((ids[index], prediction));
                            }
                        }
                    }
                }
                position += len;
                REPLAY_DONE.fetch_add(len, Ordering::Relaxed);
                // after every full chunk, not only at the end: on a Pixel 8a
                // the freed states held grew the heap 0.9 GB over the last
                // quarter of a 3.7M-review replay
                if len == REPLAY_CHUNK_REVIEWS as u64 {
                    release_freed_memory();
                }
            }
            Ok::<_, AnkiError>(())
        })();
        REPLAY_TOTAL.store(0, Ordering::Relaxed);
        result?;
        // looked up by review id; answers after this one come in order
        if let Some(calibration) = self.calibration.as_mut().filter(|_| plan.reset) {
            calibration.predictions.sort_unstable_by_key(|(id, _)| *id);
        }
        if replayed >= REPLAY_CHUNK_REVIEWS as u64 {
            release_freed_memory();
        }
        let review_count = plan.identity.review_count;
        self.identity = Some(plan.identity);
        self.checked_at_mod = Some(plan.collection_mod);
        self.checked_stamp = plan.stamp;
        if plan.reset {
            self.state_changed();
        } else {
            // Scores are kept: a queue update may refresh only some of them
            // (`rwkv_offline_scope_scores`).
            self.generation += 1;
            for scope in self.scopes.values_mut() {
                scope.answered.extend(&answered);
            }
        }
        let unsaved = match self.saved_review_count {
            Some(saved) if !plan.reset => review_count.saturating_sub(saved),
            _ => u64::MAX,
        };
        if unsaved >= SAVE_STATE_AFTER_REVIEWS {
            self.save_state(state_path);
        }
        Ok(replayed)
    }

    fn reset(&mut self) -> Result<()> {
        self.inference.reset_warm_up_state();
        self.inference
            .restore_cache_state(&self.initial_cache_state)?;
        self.identity = None;
        self.saved_review_count = None;
        self.state_changed();
        Ok(())
    }

    fn state_changed(&mut self) {
        self.generation += 1;
        self.scopes.clear();
    }
}

/// Whether the deck named `name` is a descendant of `scope`.
fn is_below(name: &str, scope: &Deck) -> bool {
    name.strip_prefix(scope.name.as_native_str())
        .is_some_and(|rest| rest.starts_with('\x1f'))
}

/// Hands the memory a big replay freed back to the system. Each review
/// replaces a card's state with a new allocation, and the allocator keeps the
/// freed ones: after a 1M-review replay 0.85 GB of the 2.3 GB were free but
/// still held, and Android counts them against the app. Android's purge is
/// ours; glibc and macOS are the engine's own helper.
fn release_freed_memory() {
    #[cfg(target_os = "android")]
    {
        use std::ffi::c_char;
        use std::ffi::c_void;
        extern "C" {
            fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
        }
        // M_PURGE from bionic's malloc.h. mallopt exists from Android 8 (API
        // 26) and minSdk is 24, so it's looked up instead of linked.
        const M_PURGE: i32 = -101;
        // SAFETY: RTLD_DEFAULT is the null handle on Android; the symbol, if
        // found, is bionic's `int mallopt(int, int)`.
        unsafe {
            let mallopt = dlsym(std::ptr::null_mut(), c"mallopt".as_ptr());
            if !mallopt.is_null() {
                let mallopt: extern "C" fn(i32, i32) -> i32 = std::mem::transmute(mallopt);
                mallopt(M_PURGE, 0);
            }
        }
    }
    crate::rwkv::release_free_memory_to_os();
}

/// `Collection::rwkv_offline_history_plan` from a history already read: what
/// a model state that has absorbed the history's expected identity must
/// replay to match it; None when it already matches. One walk, no collection.
fn history_plan(
    history: RwkvHistoricalReviews,
    collection_mod: TimestampMillis,
    stamp: Option<ReplayStamp>,
) -> Result<Option<HistoryPlan>> {
    let expected = history.input.expected_identity.clone();
    let mut reviews: Vec<Vec<ReviewInput>> = Vec::new();
    let mut review_ids = Vec::new();
    let mut total = 0;
    let fingerprint = history.walk(|review, _, _| {
        if total % REPLAY_CHUNK_REVIEWS == 0 {
            reviews.push(Vec::with_capacity(REPLAY_CHUNK_REVIEWS));
        }
        total += 1;
        let review = super::history::historical_review(&review);
        review_ids.push(review.review_id);
        reviews.last_mut().unwrap().push(answered_input(&review))
    })?;
    if fingerprint.history_is_valid {
        return Ok(None);
    }
    let mut kept = match expected {
        Some(identity) if fingerprint.history_prefix_is_valid => identity.review_count as usize,
        _ => 0,
    };
    if kept > total {
        kept = 0;
    }
    // drop the kept prefix: whole chunks, then the start of the next
    reviews.drain(..kept / REPLAY_CHUNK_REVIEWS);
    if let Some(first) = reviews.first_mut() {
        first.drain(..kept % REPLAY_CHUNK_REVIEWS);
    }
    review_ids.drain(..kept);
    Ok(Some(HistoryPlan {
        reset: kept == 0,
        reviews,
        review_ids,
        identity: RwkvHistoricalReviewIdentity {
            last_review_id: fingerprint.last_review_id,
            review_count: fingerprint.review_count,
            history_hash: fingerprint.history_hash,
        },
        collection_mod,
        stamp,
    }))
}

fn answered_input(review: &Review) -> ReviewInput {
    ReviewInput {
        card_id: review.card_id,
        note_id: Some(review.note_id),
        deck_id: Some(review.deck_id),
        preset_id: Some(review.preset_id),
        is_query: false,
        ease: Some(review.ease as u8),
        duration_millis: Some(review.duration_millis),
        card_type: Some(review.card_type),
        day_offset: Some(review.day_offset),
        current_elapsed_days: Some(review.elapsed_days),
        current_elapsed_seconds: Some(review.elapsed_seconds),
        target_retentions: [None; 4],
        enforce_grade_order: true,
    }
}

/// `query` asked `secs` later, `days` day rollovers later.
fn forecast_input(mut query: ReviewInput, secs: i64, days: i64) -> ReviewInput {
    if secs != 0 {
        query.current_elapsed_seconds = query.current_elapsed_seconds.map(|s| s + secs);
        query.current_elapsed_days = query.current_elapsed_days.map(|d| d + days);
        query.day_offset = query.day_offset.map(|d| d + days);
    }
    query
}

fn valid_probability(value: f32) -> Option<f32> {
    (value.is_finite() && (0.0..=1.0).contains(&value)).then_some(value)
}

/// The current retrievability of each row's card (cards the model can't
/// score are left out).
fn score_rows(runtime: &mut RwkvOfflineRuntime, rows: &[Row]) -> Result<Vec<ScoredCard>> {
    let inputs: Vec<_> = rows.iter().map(query_input).collect();
    let retrievabilities = runtime
        .inference
        .predict_retrievability_many_from_warm_up(inputs)?;
    Ok(rows
        .iter()
        .zip(retrievabilities)
        .filter_map(|(row, retrievability)| {
            Some(ScoredCard {
                card_id: CardId(row.card_id),
                deck_id: DeckId(row.deck_id),
                retrievability: valid_probability(retrievability)?,
                target_retention: valid_probability(row.target_retention),
            })
        })
        .collect())
}

/// The scope's current `rows` split into the scores to keep and the rows to
/// score again: those near their target, answered recently or since, or new to
/// the scope. Cards no longer in `rows` are dropped.
fn split_near_target(
    previous: &[ScoredCard],
    answered: &[CardId],
    rows: Vec<Row>,
) -> (Vec<ScoredCard>, Vec<Row>) {
    let previous: HashMap<_, _> = previous.iter().map(|card| (card.card_id, *card)).collect();
    let answered: HashSet<_> = answered.iter().copied().collect();
    let mut keep = Vec::with_capacity(rows.len());
    let mut rescore = Vec::new();
    for row in rows {
        match previous.get(&CardId(row.card_id)) {
            Some(card)
                if !answered.contains(&card.card_id)
                    && row
                        .current_elapsed_seconds
                        .is_none_or(|secs| secs >= RECENT_ANSWER_SECS)
                    && (card.retrievability
                        - card.target_retention.unwrap_or(DEFAULT_TARGET_RETENTION))
                    .abs()
                        >= NEAR_TARGET =>
            {
                keep.push(*card)
            }
            _ => rescore.push(row),
        }
    }
    (keep, rescore)
}

fn query_input(row: &Row) -> ReviewInput {
    let target = valid_probability(row.target_retention).unwrap_or(DEFAULT_TARGET_RETENTION);
    let card_type = if row.current_state_kind == "filtered" {
        4
    } else {
        match row.current_normal_state_kind.as_str() {
            "new" => 0,
            "learning" => 1,
            "review" => 2,
            "relearning" => 3,
            _ => row.card_type as i64,
        }
    };
    ReviewInput {
        card_id: row.card_id,
        note_id: Some(row.note_id),
        deck_id: Some(row.deck_id),
        preset_id: super::rwkv_stable_preset_id_from_string(&row.preset_id),
        is_query: true,
        ease: None,
        duration_millis: None,
        card_type: Some(card_type),
        day_offset: Some(row.day_offset as i64),
        current_elapsed_days: row.current_elapsed_days.map(i64::from),
        current_elapsed_seconds: row.current_elapsed_seconds.map(i64::from),
        target_retentions: [Some(target); 4],
        enforce_grade_order: row.enforce_grade_order.unwrap_or(true),
    }
}

impl Collection {
    /// Rule changes affect scores and answer targets, but not recurrent
    /// history.
    pub(crate) fn invalidate_rwkv_offline_targets(&mut self) {
        if let Some(runtime) = self.state.rwkv_offline.as_mut() {
            runtime.scopes.clear();
            runtime.curve_s90.clear();
        }
        self.state.rwkv_retrievability_scores = None;
        self.state.rwkv_count_cards.clear();
        self.state.addon_retention_populations.clear();
        self.invalidate_rwkv_review_queue();
    }

    pub(crate) fn rwkv_prepare_offline(
        &mut self,
        input: RwkvPrepareOfflineRequest,
    ) -> Result<RwkvPrepareOfflineResponse> {
        require!(!input.model_path.is_empty(), "missing RWKV model path");
        require!(
            input.model_kind.is_empty() || input.model_kind == "sm72",
            "unsupported RWKV offline model kind"
        );
        // the desktop's layout for the presets' schedulers (any preset, as its
        // Deck Options): both models, the Instant model alone, or the two-head
        // model alone
        let (mut path, mut instant_path) = (PathBuf::from(input.model_path), None);
        let mut instant_only = false;
        if !input.instant_model_path.is_empty() {
            let configs = self.storage.all_deck_config()?;
            let curve = configs.iter().any(|c| c.inner.rwkv_review_enabled);
            let instant = configs
                .iter()
                .any(|c| c.inner.rwkv_review_instant_order_enabled);
            match (curve, instant) {
                (true, true) if !input.single_model => {
                    instant_path = Some(PathBuf::from(input.instant_model_path))
                }
                (false, true) => {
                    path = PathBuf::from(input.instant_model_path);
                    instant_only = true;
                }
                _ => {}
            }
            tracing::info!(curve, instant, single = input.single_model, model = ?path, instant_model = ?instant_path, "RWKV offline models");
        }
        let model = SelectedOfflineModel {
            leak_free_inputs: input.model_kind == "sm72",
            instant_path,
            instant_only,
            model: OfflineModel {
                path,
                // empty: int8 (the fork's desktop default until build 107, which
                // moved to int4 cards + int8 notes). The app's "int4" is int4
                // cards with int8 notes: a note's state takes every answer to all
                // its cards, and int4 drift there overflowed on long histories.
                precision: match input.state_precision.as_str() {
                    "" => StatePrecision::Int8,
                    "int4" => StatePrecision::Int4CardsInt8Notes,
                    other => StatePrecision::parse(other)?,
                },
            },
        };
        let mut runtime = match self.state.rwkv_offline.take() {
            Some(runtime) if runtime.model == model => runtime,
            _ => {
                // Built on its own thread; `reviews_replayed` is then what it
                // is replaying, and the next queue build or deck count
                // installs it once done.
                *REGISTERED_MODEL.lock().unwrap() = Some(model.clone());
                let reviews_replayed = self.rwkv_offline_start_build(model, None)?;
                return Ok(RwkvPrepareOfflineResponse { reviews_replayed });
            }
        };
        // On error the runtime is dropped, and the standard scheduler applies
        // until the next successful prepare.
        let reviews_replayed = self.rwkv_offline_sync_history(&mut runtime)?;
        if reviews_replayed > 0 {
            self.invalidate_rwkv_review_queue();
        }
        *REGISTERED_MODEL.lock().unwrap() = Some(runtime.model.clone());
        self.state.rwkv_offline = Some(runtime);
        Ok(RwkvPrepareOfflineResponse { reviews_replayed })
    }

    /// Scores every Instant-enabled deck now and installs the deck count
    /// scores, in one call.
    ///
    /// `restart` is the desktop's "Rebuild RWKV State": the model state is
    /// thrown away and the whole review history is replayed. `status_only`
    /// never rebuilds; it reports the state as it is (after absorbing any new
    /// reviews, which is cheap).
    pub(crate) fn rwkv_offline_instant_pass_step(
        &mut self,
        input: RwkvOfflineInstantPassStepRequest,
    ) -> Result<RwkvOfflineInstantPassProgress> {
        if self.state.rwkv_offline.is_none() {
            self.state.rwkv_offline = self.rwkv_offline_take_built();
        }
        let Some(mut runtime) = self.state.rwkv_offline.take() else {
            return Ok(RwkvOfflineInstantPassProgress {
                replay_done: REPLAY_DONE.load(Ordering::Relaxed),
                replay_total: REPLAY_TOTAL.load(Ordering::Relaxed),
                ..Default::default()
            });
        };
        let started = Instant::now();
        let rebuild = input.restart && !input.status_only;
        let mut reviews_replayed = 0;
        let result = (|| {
            if rebuild {
                runtime.reset()?;
                runtime.checked_at_mod = None;
            }
            reviews_replayed = self.rwkv_offline_sync_history(&mut runtime)?;
            if input.status_only {
                // The app polls this until the runtime is in, then redraws the
                // deck list, which scores what it shows: scoring every scope
                // here too held the collection (0.6 s for 10,870 cards on a
                // Tab S7+) while that redraw waited. Report what the last
                // scoring left (top-most scopes: subdecks share theirs).
                let scopes = self.rwkv_offline_instant_scopes()?;
                return Ok(scopes
                    .iter()
                    .filter_map(|scope| runtime.scopes.get(&scope.id))
                    .map(|scores| scores.cards.len())
                    .sum());
            }
            self.rwkv_offline_install_deck_count_scores_inner(&mut runtime, 0)
        })();
        let reviews_absorbed = runtime
            .identity
            .as_ref()
            .map_or(0, |identity| identity.review_count);
        if runtime.identity.is_some() {
            self.state.rwkv_offline = Some(runtime);
        }
        if rebuild {
            // the retained queue was ordered with the old state's scores
            self.invalidate_rwkv_review_queue();
        }
        let scored = result? as u32;
        Ok(RwkvOfflineInstantPassProgress {
            available: true,
            scored,
            step_micros: started.elapsed().as_micros() as u64,
            reviews_absorbed,
            reviews_replayed,
            ..Default::default()
        })
    }

    /// The recall the model predicted just before each of `review_ids` (the
    /// desktop's RWKV calibration data, for the stats page's calibration and
    /// comparison graphs), for those in the [Calibration]. Without one, the
    /// runtime is rebuilt on its own thread with a full replay that records it
    /// (as after an undo: the standard order applies meanwhile; the old
    /// runtime is dropped first, two don't fit a phone with a big history),
    /// and `computing` is set until it is done. `all`: the whole history must
    /// be in it (rebuilt if it only has the newest 30%). `check_only`: never
    /// starts that rebuild (`available` says whether it is needed).
    pub(crate) fn rwkv_offline_calibration(
        &mut self,
        review_ids: Vec<i64>,
        all: bool,
        check_only: bool,
    ) -> Result<RwkvOfflineCalibrationResponse> {
        let mut out = RwkvOfflineCalibrationResponse::default();
        if self.state.rwkv_offline.is_none() {
            self.state.rwkv_offline = self.rwkv_offline_take_built();
        }
        let Some(mut runtime) = self.state.rwkv_offline.take() else {
            out.replay_done = REPLAY_DONE.load(Ordering::Relaxed);
            out.replay_total = REPLAY_TOTAL.load(Ordering::Relaxed);
            out.computing = registered_model().is_some();
            return Ok(out);
        };
        if runtime
            .calibration
            .as_ref()
            .is_none_or(|calibration| all && !calibration.all)
        {
            if check_only {
                self.state.rwkv_offline = Some(runtime);
                return Ok(out);
            }
            let model = runtime.model.clone();
            drop(runtime);
            release_freed_memory();
            self.invalidate_rwkv_review_queue();
            out.replay_total = self.rwkv_offline_start_build(model, Some(all))?;
            out.computing = true;
            out.started = true;
            return Ok(out);
        }
        if let Err(err) = self.rwkv_offline_sync_history(&mut runtime) {
            // the history changed under it (an undo): rebuilt without calibration
            tracing::warn!(?err, "RWKV calibration not read");
            out.computing = true;
        } else if let Some(calibration) = &runtime.calibration {
            out.available = true;
            for review_id in review_ids {
                if let Ok(index) = calibration
                    .predictions
                    .binary_search_by_key(&review_id, |(id, _)| *id)
                {
                    out.review_ids.push(review_id);
                    out.predictions.push(calibration.predictions[index].1);
                }
            }
        }
        if runtime.identity.is_some() {
            self.state.rwkv_offline = Some(runtime);
        }
        Ok(out)
    }

    /// Recall of review cards at later times. The model state is not
    /// touched: each card's query input is moved forward in time (seconds
    /// since its last review, and past the rollover its elapsed days and the
    /// day number), as the desktop add-on and the fork's Memorised graph do.
    /// So every number means "if you stop reviewing now".
    pub(crate) fn rwkv_offline_forecast(
        &mut self,
        input: RwkvOfflineForecastRequest,
    ) -> Result<RwkvOfflineForecastResponse> {
        let mut out = RwkvOfflineForecastResponse::default();
        if !self.rwkv_offline_enabled() {
            return Ok(out);
        }
        let ok = self.with_rwkv_offline_runtime("forecast", |col, runtime| {
            col.rwkv_offline_forecast_inner(runtime, &input, &mut out)
        });
        require!(ok, "RWKV forecast failed");
        out.available = self.state.rwkv_offline.is_some();
        // Outside the runtime closure: the deck counts score with the runtime.
        self.rwkv_offline_forecast_minimums(&input, &mut out)?;
        Ok(out)
    }

    /// "Minimum reviews per day": which cards today's deck counts add for it,
    /// and the deck list's total at the next rollover, when the minimums
    /// start again from zero reviews.
    ///
    /// Each deck row is counted in its own scope (the deck and its children),
    /// so a parent's minimum can pull cards its children's rows don't show.
    /// A card counts as its own deck's row counts it, so the forecast adds up
    /// to the rows you study from. Today's cards are recorded by the deck
    /// count itself.
    fn rwkv_offline_forecast_minimums(
        &mut self,
        input: &RwkvOfflineForecastRequest,
        out: &mut RwkvOfflineForecastResponse,
    ) -> Result<()> {
        let mut decks = Vec::new();
        for scope in self.rwkv_offline_instant_scopes()? {
            decks.extend(self.storage.deck_with_children(scope.id)?);
        }
        self.state.rwkv_count_cards.clear();
        let _ = self.deck_tree(Some(TimestampSecs::now()))?;
        let counted = std::mem::take(&mut self.state.rwkv_count_cards);
        for card in &mut out.cards {
            if let Some(own) = counted.get(&DeckId(card.deck_id)) {
                card.minimum_today = own.minimum.contains(&CardId(card.card_id));
                card.waiting = own.waiting.contains(&CardId(card.card_id));
            }
        }

        let Some(at) = input.offsets_secs.iter().position(|&secs| secs < 0) else {
            return Ok(());
        };
        let configs = self.storage.get_deck_config_map()?;
        let tomorrow = self.timing_today()?.days_elapsed + 1;
        let mut total = out
            .cards
            .iter()
            .filter(|card| card.recall[at] < card.target_retention)
            .count() as u32;
        for deck in decks {
            let own = deck.id;
            let mut tree = self.storage.child_decks(&deck)?;
            tree.insert(0, deck);
            // built for tomorrow, nothing is reviewed yet: full minimums
            let mut minimums = LimitTreeMap::build(&tree, &configs, tomorrow, false, own);
            let ids: HashSet<_> = tree.iter().map(|deck| deck.id.0).collect();
            let mut not_due = Vec::new();
            for card in out.cards.iter().filter(|card| ids.contains(&card.deck_id)) {
                if card.recall[at] < card.target_retention {
                    minimums.reserve_rwkv_reviews_if_present(DeckId(card.deck_id), 1);
                } else {
                    not_due.push((card.recall[at], card.card_id, DeckId(card.deck_id)));
                }
            }
            // the lowest recall first, as the deck count and the queue pull them
            not_due.sort_unstable_by(|a, b| a.0.total_cmp(&b.0).then(a.1.cmp(&b.1)));
            // the deepest subdeck minimums first, as the deck count fills them
            let mut selected = HashSet::new();
            for depth in minimums.rwkv_review_minimum_depths() {
                for &(_, card_id, deck_id) in &not_due {
                    if selected.contains(&card_id)
                        || !minimums
                            .rwkv_review_minimum_remaining_at_depth(deck_id, depth)
                            .unwrap_or(false)
                        || minimums.reserve_counted_rwkv_reviews(deck_id, 1)? == 0
                    {
                        continue;
                    }
                    selected.insert(card_id);
                    total += u32::from(deck_id == own);
                }
            }
        }
        out.rollover_with_minimum = Some(total);
        Ok(())
    }

    fn rwkv_offline_forecast_inner(
        &mut self,
        runtime: &mut RwkvOfflineRuntime,
        input: &RwkvOfflineForecastRequest,
        out: &mut RwkvOfflineForecastResponse,
    ) -> Result<()> {
        self.rwkv_offline_sync_history(runtime)?;
        let search = if input.search.is_empty() {
            FORECAST_SEARCH.to_string()
        } else {
            input.search.clone()
        };
        let rows = self
            .rwkv_review_input_rows_for_search(RwkvReviewInputRowsForSearchRequest {
                search,
                ..Default::default()
            })?
            .rows;
        let now = TimestampSecs::now().0;
        let cutoff = self.timing_today()?.next_day_at.0;
        let offsets: Vec<i64> = input
            .offsets_secs
            .iter()
            .map(|&secs| {
                if secs < 0 {
                    (cutoff - now).max(1)
                } else {
                    secs
                }
            })
            .collect();
        let base: Vec<_> = rows.iter().map(query_input).collect();
        let mut recall = vec![Vec::with_capacity(offsets.len()); rows.len()];
        for &secs in &offsets {
            let at = now + secs;
            // day rollovers crossed (1 s of slack, as the desktop add-on)
            let days = if at < cutoff - 1 {
                0
            } else {
                1 + (at - cutoff).max(0) / 86_400
            };
            let shifted = base
                .iter()
                .cloned()
                .map(|query| forecast_input(query, secs, days))
                .collect();
            let scores = runtime
                .inference
                .predict_retrievability_many_from_warm_up(shifted)?;
            for (card, score) in recall.iter_mut().zip(scores) {
                card.push(score);
            }
        }
        // cards the model could not score are left out (deck counts skip them too)
        out.cards = rows
            .iter()
            .zip(recall)
            .filter_map(|(row, recall)| {
                if recall.iter().any(|r| valid_probability(*r).is_none()) {
                    return None;
                }
                Some(ForecastCard {
                    card_id: row.card_id,
                    deck_id: row.deck_id,
                    target_retention: valid_probability(row.target_retention)?,
                    recall,
                    // set by rwkv_offline_forecast_minimums
                    minimum_today: false,
                    waiting: false,
                })
            })
            .collect();
        Ok(())
    }

    /// Called before a review queue is built.
    pub(crate) fn rwkv_offline_before_queue_build(&mut self, deck_id: DeckId) {
        let scored = self.with_rwkv_offline_runtime("queue build", |col, runtime| {
            let deck = col.storage.get_deck(deck_id)?;
            let Some(deck) =
                deck.filter(|_| matches!(col.rwkv_offline_deck_uses_instant(deck_id), Ok(true)))
            else {
                // not an Instant deck: scores left from another deck must not order it
                return col.set_rwkv_review_queue_score_entries(deck_id, HashMap::new());
            };
            let started = Instant::now();
            col.rwkv_offline_sync_history(runtime)?;
            let synced = started.elapsed().as_millis() as u64;
            // A subdeck shares the scores of the top-most Instant deck above it
            // (the deck list's scope), cut to its own tree: scoring it as a scope
            // of its own scored those cards again (9,881 cards: 2.1 s on a Tab
            // S7+, seconds after the deck list had scored them).
            let scope = col
                .rwkv_offline_shared_scope(&deck)?
                .unwrap_or_else(|| deck.clone());
            let mut cards =
                col.rwkv_offline_scope_scores(runtime, &scope, QUEUE_SCORE_MAX_AGE_SECS, true)?;
            if scope.id != deck.id {
                let tree = col.rwkv_offline_deck_tree_ids(&deck)?;
                cards.retain(|card| tree.contains(&card.deck_id));
            }
            let scored = started.elapsed().as_millis() as u64;
            let entries = col.rwkv_offline_score_entries(&deck, &cards)?;
            tracing::info!(
                synced,
                scored,
                ms = started.elapsed().as_millis() as u64,
                cards = cards.len(),
                "RWKV queue scores"
            );
            col.set_rwkv_review_queue_score_entries(deck_id, entries)
        });
        if !scored {
            // Scores from before the failure would still order the queue, and a
            // card answered since then would keep its old, low score.
            let _ = self.set_rwkv_review_queue_score_entries(deck_id, HashMap::new());
        }
    }

    /// Called after `card_id` was answered (`mod_before` is the collection's
    /// modification time before the answer). The answer goes into the model
    /// state now, as the desktop's `record_reviewer_answer` does, so the next
    /// queue build needn't check the whole review history. The retained queue
    /// keeps its order; only the answered card's score is patched, as the
    /// desktop's `PatchAnsweredCardRwkvReviewQueueScore` does (rebuilding the
    /// queue after every answer re-scored the scope: ~100 ms of each
    /// Good → next card on a Tab S7+ with 9,900 due cards), until "Update the
    /// RWKV queue every" N answers: then the queue is rebuilt, re-scoring the
    /// scope as the desktop's refresh does (else every other card kept its
    /// score, and the count, until the reviewer was left).
    pub(crate) fn rwkv_offline_after_answer(
        &mut self,
        card_id: CardId,
        mod_before: TimestampMillis,
        queue_exhausted: bool,
    ) {
        if !self.rwkv_offline_enabled() {
            return;
        }
        if let Some(mut runtime) = self.state.rwkv_offline.take() {
            let started = Instant::now();
            match self.rwkv_offline_absorb_answer(&mut runtime, card_id, mod_before) {
                Ok(absorbed) => tracing::info!(
                    absorbed,
                    ms = started.elapsed().as_millis() as u64,
                    "RWKV answer"
                ),
                // the next history sync checks the whole history
                Err(err) => tracing::warn!(?err, "RWKV answer not absorbed"),
            }
            self.state.rwkv_offline = Some(runtime);
        }
        let deck_id = self.get_current_deck_id();
        if matches!(self.rwkv_offline_deck_uses_instant(deck_id), Ok(true)) {
            let started = Instant::now();
            let mut refresh_due = false;
            let patched = self.with_rwkv_offline_runtime("answer score", |col, runtime| {
                let Some(deck) = col.storage.get_deck(deck_id)? else {
                    return Ok(());
                };
                // The fast append can decline an answer when another operation
                // changed the collection. Bring the model up to date before
                // scoring the card or checking the refresh interval.
                col.rwkv_offline_sync_history(runtime)?;
                let rows = col
                    .rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                        card_ids: vec![card_id.0],
                        include_new_cards: true,
                        ..Default::default()
                    })?
                    .rows;
                let scored = score_rows(runtime, &rows)?;
                let entry = col
                    .rwkv_offline_score_entries(&deck, &scored)?
                    .remove(&card_id);
                // the other recently answered cards' repeat spacing moved by one
                let intervening = col.rwkv_offline_intervening_reviews(&deck)?;
                col.patch_answered_card_rwkv_review_queue_score_entry(deck_id, card_id, entry)?;
                col.update_rwkv_review_queue_intervening_reviews(deck_id, intervening)?;
                // the same check as rwkv_offline_scope_scores' update_due
                let scope = col.rwkv_offline_shared_scope(&deck)?.unwrap_or(deck);
                let interval = col
                    .rwkv_offline_deck_config(scope.id)?
                    .map_or(1, |c| c.inner.rwkv_review_refresh_interval.max(1));
                if let Some(scores) = runtime.scopes.get_mut(&scope.id) {
                    if queue_exhausted && !scores.answered.is_empty() {
                        scores.force_refresh = true;
                    }
                    refresh_due =
                        scores.force_refresh || scores.answered.len() >= interval as usize;
                }
                Ok(())
            });
            tracing::info!(
                patched,
                refresh_due,
                ms = started.elapsed().as_millis() as u64,
                "RWKV answer score"
            );
            if !patched || refresh_due {
                self.invalidate_rwkv_review_queue();
            }
        }
    }

    /// The raw kind desktop puts on an answer when RWKV is active.
    pub(crate) fn rwkv_offline_answer_review_kind(
        &mut self,
        card_id: CardId,
        current_state: &CardState,
        next_state: &CardState,
        config_active: bool,
        answered_at: TimestampMillis,
        timing: &SchedTimingToday,
    ) -> Result<Option<u32>> {
        if !self.rwkv_offline_enabled() || !config_active {
            return Ok(None);
        }
        let kind = match current_state {
            CardState::Normal(NormalState::New(_) | NormalState::Learning(_)) => 0,
            CardState::Normal(NormalState::Review(_)) => self
                .rwkv_offline_same_day_review_kind(
                    card_id,
                    current_state,
                    config_active,
                    answered_at,
                    timing,
                )?
                .unwrap_or(1),
            CardState::Normal(NormalState::Relearning(_)) => 2,
            CardState::Filtered(FilteredState::Rescheduling(current))
                if matches!(
                    current.original_state,
                    NormalState::New(_) | NormalState::Learning(_)
                ) && matches!(next_state, CardState::Filtered(FilteredState::Rescheduling(next))
                        if matches!(next.original_state, NormalState::Learning(learning) if learning.memory_state.is_none())) =>
            {
                // Filtered factor-0 learning rows would be discarded as cramming.
                0
            }
            CardState::Filtered(_) => 3,
        };
        Ok(Some(kind))
    }

    /// After the answer transaction commits, retain only synthetic Filtered
    /// repeats; a real filtered-deck answer must not extend that sequence.
    pub(crate) fn rwkv_offline_record_answer_kind(
        &mut self,
        answer: &CardAnswer,
        id: RevlogId,
        config_active: bool,
    ) {
        self.state
            .rwkv_synthetic_filtered_answers
            .remove(&answer.card_id);
        if self.rwkv_offline_enabled()
            && config_active
            && answer.from_queue
            && matches!(
                answer.current_state,
                CardState::Normal(NormalState::Review(_))
            )
            && answer.rwkv_review_kind == Some(3)
        {
            self.state
                .rwkv_synthetic_filtered_answers
                .insert(answer.card_id, id);
        }
    }

    /// The revlog kind the desktop records when a review card is answered
    /// again on the day of its previous answer (`CardAnswer.rwkv_review_kind`,
    /// see `_rwkv_review_state_for_live_context` in rwkv_scheduler.py), so
    /// that reviews made here replay like reviews made on the desktop.
    pub(crate) fn rwkv_offline_same_day_review_kind(
        &mut self,
        card_id: CardId,
        current_state: &CardState,
        config_active: bool,
        answered_at: TimestampMillis,
        timing: &SchedTimingToday,
    ) -> Result<Option<u32>> {
        if !self.rwkv_offline_enabled()
            || !config_active
            || !matches!(current_state, CardState::Normal(NormalState::Review(_)))
        {
            return Ok(None);
        }
        let previous = self
            .storage
            .db
            .query_row(
                &format!(
                    "select r.id, r.ease, r.type from revlog r
                     join cards c on c.id = r.cid where r.cid = ? and {}
                     and r.id > coalesce((select max(id) from revlog
                       where cid = r.cid and type = 4 and ease = 0 and factor = 0), 0)
                     and r.id > ({RWKV_HISTORY_RESET_AFTER_SQL})
                     order by r.id desc limit 1",
                    RWKV_COUNTED_REVIEW
                ),
                [card_id.0],
                |row| {
                    Ok((
                        row.get::<_, i64>(0)?,
                        row.get::<_, i64>(1)?,
                        row.get::<_, i64>(2)?,
                    ))
                },
            )
            .optional()?;
        let Some((previous_id, previous_ease, previous_kind)) = previous else {
            return Ok(None);
        };
        if rwkv_historical_day_offset(previous_id, timing)
            != rwkv_historical_day_offset(answered_at.0, timing)
        {
            return Ok(None);
        }
        const RELEARNING: u32 = 2;
        const FILTERED: u32 = 3;
        Ok(match (previous_kind, previous_ease) {
            (1, 1) | (2, 1 | 2) => Some(RELEARNING),
            (0..=2, _) => Some(FILTERED),
            // Only a synthetic repeat chains; a real filtered-deck answer
            // returns to Review when the card is answered normally.
            (3, _)
                if self.state.rwkv_synthetic_filtered_answers.get(&card_id)
                    == Some(&RevlogId(previous_id)) =>
            {
                Some(FILTERED)
            }
            _ => None,
        })
    }

    /// RWKV-Curve's unrounded intervals in days for the card's four answers
    /// at `targets`, as the desktop's reviewer computes them before it draws
    /// the answer buttons (`update_reviewer_scheduling_states` and
    /// `rwkv_review_input` in rwkv_scheduler.py); their S90s are kept for the
    /// answer ([Self::rwkv_offline_curve_s90]). None, so FSRS's intervals
    /// apply, while the model state isn't ready or the card can't be scored.
    pub(crate) fn rwkv_offline_curve(
        &mut self,
        card_id: CardId,
        current_state: &CardState,
        targets: [f32; 4],
        enforce_grade_order: bool,
        timing: &SchedTimingToday,
    ) -> Option<[Option<f32>; 4]> {
        if !self.rwkv_offline_enabled() {
            return None;
        }
        let mut curve = None;
        self.with_rwkv_offline_runtime("curve", |col, runtime| {
            col.rwkv_offline_sync_history(runtime)?;
            let Some(row) = col
                .rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                    card_ids: vec![card_id.0],
                    include_new_cards: true,
                    ..Default::default()
                })?
                .rows
                .pop()
            else {
                return Ok(());
            };
            let mut input = query_input(&row);
            if matches!(current_state, CardState::Filtered(_)) {
                // The batch row describes the underlying card, but Curve's
                // hypothetical answers use the actual filtered study state.
                input.card_type = Some(4);
                let Some(mut card) = col.storage.get_card(card_id)? else {
                    return Ok(());
                };
                col.populate_rwkv_last_review_times(std::slice::from_mut(&mut card))?;
                input.current_elapsed_days = card
                    .last_review_time
                    .map(|last| i64::from(timing.next_day_at.elapsed_days_since_clamped(last)));
                input.current_elapsed_seconds = card
                    .last_review_time
                    .map(|last| i64::from(TimestampSecs::now().elapsed_secs_since_clamped(last)));
            } else if let CardState::Normal(NormalState::Learning(learning)) = current_state {
                // Desktop uses the scheduler's learning-step elapsed time.
                input.current_elapsed_days = None;
                input.current_elapsed_seconds = Some(i64::from(learning.elapsed_secs));
            }
            // A review card answered earlier today is asked as the relearning
            // or filtered review its answer will be recorded as (the desktop's
            // `_rwkv_review_state_for_live_context`).
            match col.rwkv_offline_same_day_review_kind(
                card_id,
                current_state,
                true,
                TimestampMillis::now(),
                timing,
            )? {
                Some(2) => input.card_type = Some(3),
                Some(3) => input.card_type = Some(4),
                _ => {}
            }
            input.target_retentions = targets.map(Some);
            input.enforce_grade_order = enforce_grade_order;
            // KUMA3_CURVE_DEBUG: the query and its answers, to compare with
            // the desktop's (kuma3_curve_plan.md)
            let debug = std::env::var_os("KUMA3_CURVE_DEBUG")
                .is_some()
                .then(|| format!("{input:?}"));
            let (intervals, s90s) = runtime.inference.curve_from_warm_up(input)?;
            if let Some(input) = debug {
                eprintln!("CURVEDEBUG {input} intervals {intervals:?} s90s {s90s:?}");
            }
            if runtime.curve_s90.len() >= 64 {
                runtime.curve_s90.clear(); // only the cards on screen matter
            }
            runtime.curve_s90.insert(card_id, s90s);
            curve = Some(intervals);
            Ok(())
        });
        curve
    }

    /// The S90 of `card_id`'s Curve answer `rating_index` (0 = Again), if
    /// [Self::rwkv_offline_curve] gave its answer buttons.
    pub(crate) fn rwkv_offline_curve_s90(
        &self,
        card_id: CardId,
        rating_index: usize,
    ) -> Option<f32> {
        self.state.rwkv_offline.as_ref()?.curve_s90.get(&card_id)?[rating_index]
    }

    /// "Reschedule Cards with RWKV-Curve Intervals" without the desktop's
    /// Python (`reschedule_rwkv_review_cards` in rwkv_scheduler.py): each
    /// review card under `deck_id` whose preset uses RWKV-Curve gets the
    /// whole-day interval where its current curve meets its target, counted
    /// from its last review, and that curve's S90 as stability. As on the
    /// desktop, no fuzz or load balancing.
    pub(crate) fn rwkv_offline_curve_reschedule(
        &mut self,
        deck_id: DeckId,
    ) -> Result<OpOutput<usize>> {
        let mut items = None;
        self.with_rwkv_offline_runtime("curve reschedule", |col, runtime| {
            col.rwkv_offline_sync_history(runtime)?;
            let decks = col.storage.get_decks_map()?;
            let configs = col.storage.get_deck_config_map()?;
            let uses_curve = |deck: i64| {
                decks
                    .get(&DeckId(deck))
                    .and_then(|deck| deck.config_id())
                    .and_then(|id| configs.get(&id))
                    .is_some_and(|config| config.inner.rwkv_review_enabled)
            };
            let rows: Vec<_> = col
                .rwkv_review_input_rows_for_deck_review_queue(
                    RwkvReviewInputRowsForDeckReviewQueueRequest {
                        deck_id: deck_id.0,
                        include_new_cards: false,
                        ..Default::default()
                    },
                )?
                .rows
                .into_iter()
                .filter(|row| uses_curve(row.deck_id))
                .collect();
            // ponytail: the preset's desired retention, not dynamic DR (the
            // desktop resolves that per card); add it if a Curve user needs it
            let mut found = Vec::with_capacity(rows.len());
            for rows in rows.chunks(DEFAULT_REVIEW_BATCH_SIZE as usize) {
                let current = runtime
                    .inference
                    .curve_current_many(rows.iter().map(query_input).collect())?;
                for (row, (interval, s90)) in rows.iter().zip(current) {
                    if let (Some(interval_days @ 1..), Some(s90), Some(elapsed_days)) = (
                        interval,
                        s90.filter(|s90| s90.is_finite() && *s90 > 0.0),
                        row.current_elapsed_days,
                    ) {
                        found.push(RwkvReviewRescheduleItem {
                            card_id: CardId(row.card_id),
                            interval_days,
                            elapsed_days,
                            s90,
                            target_retention: valid_probability(row.target_retention),
                        });
                    }
                }
            }
            items = Some(found);
            Ok(())
        });
        let Some(items) = items else {
            invalid_input!("RWKV is still getting ready: try again in a moment")
        };
        self.apply_rwkv_review_reschedule(items)
    }

    /// Called before deck counts are computed.
    pub(crate) fn rwkv_offline_before_deck_counts(&mut self) {
        let scored = self.with_rwkv_offline_runtime("deck counts", |col, runtime| {
            col.rwkv_offline_sync_history(runtime)?;
            col.rwkv_offline_install_deck_count_scores_inner(
                runtime,
                DECK_COUNT_SCORE_MAX_AGE_SECS,
            )?;
            Ok(())
        });
        if !scored {
            self.clear_rwkv_deck_count_scores();
        }
    }

    fn rwkv_offline_enabled(&self) -> bool {
        self.state.rwkv_offline.is_some() || registered_model().is_some()
    }

    /// Run `func` with the runtime. Returns false if it failed, so the caller
    /// can clear what an earlier run installed; true also when there is no
    /// runtime (nothing was ever installed).
    fn with_rwkv_offline_runtime(
        &mut self,
        stage: &str,
        func: impl FnOnce(&mut Collection, &mut RwkvOfflineRuntime) -> Result<()>,
    ) -> bool {
        let mut runtime = match self.state.rwkv_offline.take() {
            Some(runtime) => runtime,
            None => {
                // Not built yet, or the collection was reopened (e.g. after a
                // full sync) since the client's prepare call.
                let Some(model) = registered_model() else {
                    return true;
                };
                match self.rwkv_offline_take_built() {
                    Some(runtime) => runtime,
                    None => {
                        if let Err(err) = self.rwkv_offline_start_build(model, None) {
                            tracing::warn!(?err, "RWKV offline build not started");
                        }
                        return true;
                    }
                }
            }
        };
        let result = func(self, &mut runtime);
        // without identity (failed replay, changed history) it is being rebuilt
        if runtime.identity.is_some() {
            self.state.rwkv_offline = Some(runtime);
        }
        if let Err(err) = &result {
            tracing::warn!(
                ?err,
                stage,
                "RWKV offline scoring failed; using the standard order"
            );
        }
        result.is_ok()
    }

    fn rwkv_offline_deck_uses_instant(&mut self, deck_id: DeckId) -> Result<bool> {
        Ok(self
            .rwkv_offline_deck_config(deck_id)?
            .is_some_and(|config| config.inner.rwkv_review_instant_order_enabled))
    }

    fn rwkv_offline_deck_config(&mut self, deck_id: DeckId) -> Result<Option<DeckConfig>> {
        let Some(config_id) = self
            .storage
            .get_deck(deck_id)?
            .and_then(|deck| deck.config_id())
        else {
            return Ok(None);
        };
        self.storage.get_deck_config(config_id)
    }

    /// Bring the model state in line with the revlog. Returns the number of
    /// reviews replayed.
    fn rwkv_offline_sync_history(&mut self, runtime: &mut RwkvOfflineRuntime) -> Result<u64> {
        let collection_mod = self.storage.get_collection_timestamps()?.collection_change;
        if runtime.identity.is_some() && runtime.checked_at_mod == Some(collection_mod) {
            return Ok(0);
        }
        // Search and field rules can change their targets when notes, tags,
        // decks, or scheduling data change. Keep the recurrent state, but do
        // not reuse scores computed from an older collection snapshot.
        if runtime.identity.is_some()
            && crate::scheduler::addon_desired_retention::AddonRetentionRules::from_collection(self)
                .is_some()
        {
            runtime.scopes.clear();
            runtime.curve_s90.clear();
            self.state.rwkv_retrievability_scores = None;
            self.state.addon_retention_populations.clear();
            self.invalidate_rwkv_review_queue();
        }
        let stamp = self.rwkv_offline_replay_stamp()?;
        if runtime.identity.is_some() && stamp.is_some() && stamp == runtime.checked_stamp {
            // Only what the replay doesn't read changed (deck options, notes).
            runtime.checked_at_mod = Some(collection_mod);
            return Ok(0);
        }
        match self.rwkv_offline_history_plan(runtime.identity.as_ref(), collection_mod, stamp)? {
            Some(plan) if plan.reset && runtime.identity.is_some() => {
                // The history changed under the state (an undo, a full sync):
                // replaying all of it here would hold the collection, close to a
                // minute on a big one. The runtime without identity is dropped by
                // the caller and rebuilt on its own thread.
                runtime.identity = None;
                self.rwkv_offline_start_build(runtime.model.clone(), None)?;
                invalid_input!("RWKV history changed; rebuilding in the background")
            }
            Some(plan) => runtime.apply_history_plan(plan, &self.rwkv_offline_state_path()),
            None => {
                runtime.checked_at_mod = Some(collection_mod);
                runtime.checked_stamp = stamp;
                Ok(0)
            }
        }
    }

    fn rwkv_offline_history_request(&mut self) -> Result<RwkvHistoricalReviewFingerprintRequest> {
        // The synced collection setting wins over the legacy per-preset flags,
        // as `_rwkv_collection_config_state` in rwkv_scheduler.py.
        let dynamic_preset_replay = match self.get_config_optional("rwkvDynamicPresetReplay") {
            Some(setting) => setting,
            None => self.storage.all_deck_config()?.iter().any(|config| {
                (config.inner.rwkv_review_enabled || config.inner.rwkv_review_instant_order_enabled)
                    && config.inner.rwkv_review_dynamic_preset_replay
            }),
        };
        Ok(RwkvHistoricalReviewFingerprintRequest {
            dynamic_preset_replay,
            ..Default::default()
        })
    }

    /// See [ReplayStamp].
    fn rwkv_offline_replay_stamp(&mut self) -> Result<Option<ReplayStamp>> {
        // ponytail: a review edited in place (same count and newest id) isn't
        // seen; nothing on the phone does that. Hash the rows if something does.
        let (revlog_rows, last_review_id) = self.storage.db.query_row(
            "select count(), coalesce(max(id), 0) from revlog",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )?;
        self.rwkv_offline_replay_stamp_with(revlog_rows, last_review_id)
    }

    /// [ReplayStamp] for a revlog of `revlog_rows` rows, the newest
    /// `last_review_id`.
    fn rwkv_offline_replay_stamp_with(
        &mut self,
        revlog_rows: u64,
        last_review_id: i64,
    ) -> Result<Option<ReplayStamp>> {
        if self
            .get_config_optional::<FsrsPresetOverlay, _>(FSRS_PRESET_OVERLAY_CONFIG_KEY)
            .is_some_and(|overlay| !overlay.rules.is_empty() || !overlay.simulator_rules.is_empty())
        {
            return Ok(None);
        }
        let mut hash = fnv::FnvHasher::default();
        {
            let mut cards = self.storage.db.prepare(&format!(
                "select c.id, c.nid, case when c.odid != 0 then c.odid else c.did end,
                        c.type = 0, ({RWKV_HISTORY_RESET_AFTER_SQL}) from cards c order by c.id"
            ))?;
            let mut rows = cards.query([])?;
            while let Some(row) = rows.next()? {
                for column in 0..5 {
                    hash.write_i64(row.get(column)?);
                }
            }
        }
        let mut decks: Vec<_> = self
            .storage
            .get_decks_map()?
            .into_values()
            .map(|deck| (deck.id.0, deck.config_id().map_or(-1, |id| id.0)))
            .collect();
        decks.sort_unstable();
        let mut configs: Vec<_> = self
            .storage
            .all_deck_config()?
            .into_iter()
            .map(|config| config.id.0)
            .collect();
        configs.sort_unstable();
        hash.write_usize(decks.len());
        for (deck, config) in decks {
            hash.write_i64(deck);
            hash.write_i64(config);
        }
        hash.write_usize(configs.len());
        for config in configs {
            hash.write_i64(config);
        }
        // The day boundary as Clanki keys it: the same on every day, and all
        // the replay's day numbers depend on.
        let timing = self.timing_today()?;
        hash.write_i64(timing.next_day_at.0.rem_euclid(86_400));
        hash.write_i64(timing.days_elapsed as i64 - timing.next_day_at.0.div_euclid(86_400));
        hash.write_u8(self.rwkv_offline_history_request()?.dynamic_preset_replay as u8);
        Ok(Some(ReplayStamp {
            revlog_rows,
            last_review_id,
            rest: hash.finish(),
        }))
    }

    /// Appends the answer to `card_id` to the model state, when nothing else
    /// changed the collection since the history was last checked and
    /// appending gives what a replay would. Returns whether it did.
    fn rwkv_offline_absorb_answer(
        &mut self,
        runtime: &mut RwkvOfflineRuntime,
        card_id: CardId,
        mod_before: TimestampMillis,
    ) -> Result<bool> {
        let Some(identity) = runtime.identity.clone() else {
            return Ok(false);
        };
        if runtime.checked_at_mod != Some(mod_before) {
            return Ok(false);
        }
        let history = self.rwkv_offline_history_request()?;
        let Some((review, identity)) =
            self.rwkv_historical_review_appended(card_id, &identity, history)?
        else {
            return Ok(false);
        };
        let collection_mod = self.storage.get_collection_timestamps()?.collection_change;
        runtime.apply_history_plan(
            HistoryPlan {
                reset: false,
                reviews: vec![vec![answered_input(&review)]],
                review_ids: vec![review.review_id],
                identity,
                collection_mod,
                stamp: runtime
                    .checked_stamp
                    .map(|stamp| stamp.appended(review.review_id)),
            },
            &self.rwkv_offline_state_path(),
        )?;
        Ok(true)
    }

    /// What a model state that has absorbed `absorbed` must replay to match
    /// the revlog; None when it already matches.
    fn rwkv_offline_history_plan(
        &mut self,
        absorbed: Option<&RwkvHistoricalReviewIdentity>,
        collection_mod: TimestampMillis,
        stamp: Option<ReplayStamp>,
    ) -> Result<Option<HistoryPlan>> {
        let mut request = self.rwkv_offline_history_request()?;
        request.expected_identity = absorbed.filter(|id| id.review_count > 0).cloned();
        let history = self.read_rwkv_historical_reviews(request, Default::default())?;
        history_plan(history, collection_mod, stamp)
    }

    /// Starts loading the runtime (state file, then any replay) on its own
    /// thread, unless that is already under way for this collection: on a
    /// phone it takes seconds, and close to a minute for a full replay of a
    /// big history, which must not hold the collection. Until it is installed
    /// (`rwkv_offline_take_built`) the standard order applies. Returns the
    /// number of reviews it replays. With `calibrate` (`Some(all)`), the state
    /// file is not used: the whole history is replayed, recording a
    /// [Calibration].
    fn rwkv_offline_start_build(
        &mut self,
        model: SelectedOfflineModel,
        calibrate: Option<bool>,
    ) -> Result<u64> {
        let mut building = BUILDING.lock().unwrap();
        if building
            .as_ref()
            .is_some_and(|(path, _)| *path == self.col_path)
        {
            return Ok(0);
        }
        // Only the identity is read here; the states are loaded on the thread.
        let state_path = self.rwkv_offline_state_path();
        let planned_for = offline_state::header(&model)
            .and_then(|header| offline_state::read_identity(&state_path, &header))
            .ok()
            .and_then(|bytes| RwkvHistoricalReviewIdentity::decode(bytes.as_slice()).ok())
            .filter(|_| calibrate.is_none());
        let collection_mod = self.storage.get_collection_timestamps()?.collection_change;
        // Only reading the history needs the collection. Walking it (the
        // check against the state file, the replay inputs) is done on the
        // thread: it held the collection ~22 s for a 4M-review history on a
        // Pixel 8a, and the app's main thread waits for the open.
        let read_started = Instant::now();
        let mut request = self.rwkv_offline_history_request()?;
        request.expected_identity = planned_for.clone().filter(|id| id.review_count > 0);
        let history = self.read_rwkv_historical_reviews(request, Default::default())?;
        // That read scanned all of revlog: counting it again for the stamp
        // was a second pass (7 s of a 15 s start on a Tab S7+, 3.8M reviews).
        let (revlog_rows, last_review_id) = history.revlog_scan;
        let stamp = self.rwkv_offline_replay_stamp_with(revlog_rows, last_review_id)?;
        // what the app waits for at a start ("Processing..." on the deck list)
        tracing::info!(
            ms = read_started.elapsed().as_millis() as u64,
            reviews = history.rows.len(),
            "RWKV offline history read under the collection"
        );
        // ponytail: an estimate (for the app's message), assumes the state
        // file is a valid prefix; the walk on the thread decides
        let replaying = (history.rows.len() as u64)
            .saturating_sub(planned_for.as_ref().map_or(0, |id| id.review_count));
        let handle = std::thread::spawn(move || -> Result<Box<RwkvOfflineRuntime>> {
            let started = Instant::now();
            let plan = history_plan(history, collection_mod, stamp)?;
            let mut runtime = if let Some(all) = calibrate {
                let mut runtime = RwkvOfflineRuntime::load(model)?;
                runtime.calibration = Some(Calibration {
                    all,
                    ..Default::default()
                });
                runtime
            } else {
                RwkvOfflineRuntime::load_with_saved_state(model, &state_path)?
            };
            // A file that changed since the plan was made is left to the
            // history sync after install.
            if runtime.identity == planned_for {
                match plan {
                    Some(plan) => {
                        runtime.apply_history_plan(plan, &state_path)?;
                    }
                    None => {
                        runtime.checked_at_mod = Some(collection_mod);
                        runtime.checked_stamp = stamp;
                    }
                }
            }
            tracing::info!(
                replaying,
                ms = started.elapsed().as_millis() as u64,
                "RWKV offline runtime built"
            );
            Ok(Box::new(runtime))
        });
        // ponytail: a build for another collection is dropped, not stopped: it
        // runs on (memory) until done; join it first if profiles get switched a lot.
        *building = Some((self.col_path.clone(), handle));
        Ok(replaying)
    }

    /// The runtime from `rwkv_offline_start_build`, once it is done.
    fn rwkv_offline_take_built(&mut self) -> Option<Box<RwkvOfflineRuntime>> {
        let mut building = BUILDING.lock().unwrap();
        if !building
            .as_ref()
            .is_some_and(|(path, handle)| *path == self.col_path && handle.is_finished())
        {
            return None;
        }
        let (_, handle) = building.take()?;
        drop(building);
        match handle.join() {
            Ok(Ok(runtime)) => {
                // The precision was changed while it was built: build again.
                // ponytail: the old build runs to its end first; cancel it if
                // changes during long replays matter
                if let Some(model) = registered_model().filter(|model| *model != runtime.model) {
                    if let Err(err) = self.rwkv_offline_start_build(model, None) {
                        tracing::warn!(?err, "RWKV offline build not started");
                    }
                    return None;
                }
                // queues built meanwhile have the standard order
                self.invalidate_rwkv_review_queue();
                Some(runtime)
            }
            Ok(Err(err)) => {
                tracing::warn!(?err, "RWKV offline model failed to load; disabled");
                *REGISTERED_MODEL.lock().unwrap() = None;
                None
            }
            Err(_) => {
                tracing::warn!("RWKV offline build panicked; disabled");
                *REGISTERED_MODEL.lock().unwrap() = None;
                None
            }
        }
    }

    /// The state file: `collection.rwkv-offline` next to the collection.
    fn rwkv_offline_state_path(&self) -> PathBuf {
        self.col_path.with_extension("rwkv-offline")
    }
}

/// The [Calibration] file next to the state file at `state_path`.
fn calibration_path(state_path: &Path) -> PathBuf {
    state_path.with_extension("rwkv-calibration")
}

impl Collection {
    /// Current retrievability of the scoreable cards in `deck` and its
    /// children.
    ///
    /// With `partial` (queue builds), the deck's "Update the RWKV queue every
    /// N answers" and "Use faster, approximate queue updates" apply as on the
    /// desktop: between updates only the answered cards are rescored, and an
    /// approximate update rescores those plus the cards most likely to come
    /// next instead of the whole deck.
    fn rwkv_offline_scope_scores(
        &mut self,
        runtime: &mut RwkvOfflineRuntime,
        deck: &Deck,
        max_age_secs: i64,
        partial: bool,
    ) -> Result<Vec<ScoredCard>> {
        let days_elapsed = self.timing_today()?.days_elapsed;
        let now = TimestampSecs::now();
        let config = self.rwkv_offline_deck_config(deck.id)?;
        let include_new_cards = config.as_ref().is_some_and(|config| {
            matches!(
                config.inner.new_card_gather_priority(),
                NewCardGatherPriority::DescendingRetrievability
                    | NewCardGatherPriority::AscendingRetrievability
            )
        });
        if let Some(scope) = runtime
            .scopes
            .get_mut(&deck.id)
            .filter(|scope| scope.days_elapsed == days_elapsed)
        {
            let expired = now.0 - scope.computed_at.0 >= max_age_secs;
            // A partial queue update can mark the scope current while other
            // cards still have older scores. Deck counts need a full update.
            if scope.generation == runtime.generation
                && (partial || scope.fully_computed_generation == runtime.generation)
                && !expired
                && !scope.force_refresh
            {
                return Ok(scope.cards.clone());
            }
            let interval = config
                .as_ref()
                .map_or(1, |c| c.inner.rwkv_review_refresh_interval.max(1));
            let update_due =
                scope.force_refresh || expired || scope.answered.len() >= interval as usize;
            let approximate = config
                .as_ref()
                .is_some_and(|c| c.inner.rwkv_review_candidate_refresh_enabled);
            if partial && (!update_due || approximate) {
                let mut card_ids = scope.answered.clone();
                if update_due {
                    let batch = config.as_ref().map_or(DEFAULT_REVIEW_BATCH_SIZE, |c| {
                        match c.inner.rwkv_review_batch_size {
                            0 => DEFAULT_REVIEW_BATCH_SIZE,
                            n => n.clamp(MIN_REVIEW_BATCH_SIZE, MAX_REVIEW_BATCH_SIZE),
                        }
                    }) as usize;
                    let descending = config.as_ref().is_some_and(|c| {
                        c.inner.review_order() == ReviewCardOrder::RetrievabilityDescending
                    });
                    // ponytail: relative overdueness is ranked by recall over
                    // target, not the queue's exact formula; fine for picking
                    // which cards to recheck.
                    let rank = |card: &ScoredCard| {
                        let r = card.retrievability / card.target_retention.unwrap_or(1.0);
                        if descending {
                            -r
                        } else {
                            r
                        }
                    };
                    let mut ranked: Vec<_> = scope.cards.iter().collect();
                    let batch = batch.min(ranked.len());
                    if batch > 0 && batch < ranked.len() {
                        ranked.select_nth_unstable_by(batch, |a, b| rank(a).total_cmp(&rank(b)));
                    }
                    card_ids.extend(ranked[..batch].iter().map(|card| card.card_id));
                }
                let rows = self
                    .rwkv_review_input_rows_for_cards(RwkvReviewInputRowsForCardsRequest {
                        card_ids: card_ids.iter().map(|id| id.0).collect(),
                        include_new_cards,
                        ..Default::default()
                    })?
                    .rows;
                let tree = self.rwkv_offline_deck_tree_ids(deck)?;
                let rescored = score_rows(runtime, &rows)?;
                let scope = runtime.scopes.get_mut(&deck.id).unwrap();
                let rechecked: HashSet<_> = card_ids.into_iter().collect();
                // Rechecked cards that are no longer scoreable (or have left the
                // deck) are dropped; the rest get their new score. Cards answered
                // in other decks are rechecked too, but only this deck's are kept.
                scope
                    .cards
                    .retain(|card| !rechecked.contains(&card.card_id));
                scope.cards.extend(
                    rescored
                        .into_iter()
                        .filter(|card| tree.contains(&card.deck_id)),
                );
                if update_due {
                    scope.answered.clear();
                    scope.force_refresh = false;
                    scope.computed_at = now;
                }
                scope.generation = runtime.generation;
                return Ok(scope.cards.clone());
            }
        }

        let rows = self
            .rwkv_review_input_rows_for_deck_review_queue(
                RwkvReviewInputRowsForDeckReviewQueueRequest {
                    deck_id: deck.id.0,
                    include_disabled_decks: false,
                    include_new_cards,
                },
            )?
            .rows;
        // Only the clock moved since every card was scored: re-score the cards
        // near their target, and any the scope didn't have (moved, unsuspended).
        if let Some(scope) = runtime.scopes.get(&deck.id).filter(|scope| {
            !partial
                && scope.days_elapsed == days_elapsed
                && scope.fully_computed_generation == runtime.generation
                && now.0 - scope.fully_computed_at.0 < FULL_SCORE_MAX_AGE_SECS
        }) {
            let (mut cards, rescore) = split_near_target(&scope.cards, &scope.answered, rows);
            cards.extend(score_rows(runtime, &rescore)?);
            let scope = runtime.scopes.get_mut(&deck.id).unwrap();
            scope.cards = cards.clone();
            scope.computed_at = now;
            scope.answered.clear();
            return Ok(cards);
        }
        let cards = score_rows(runtime, &rows)?;
        runtime.scopes.insert(
            deck.id,
            ScopeScores {
                generation: runtime.generation,
                days_elapsed,
                computed_at: now,
                fully_computed_at: now,
                fully_computed_generation: runtime.generation,
                cards: cards.clone(),
                answered: Vec::new(),
                force_refresh: false,
            },
        );
        Ok(cards)
    }

    /// Score entries for `cards`, with the repeat spacing the desktop derives
    /// from the most recent reviews in `deck`'s tree.
    fn rwkv_offline_score_entries(
        &mut self,
        deck: &Deck,
        cards: &[ScoredCard],
    ) -> Result<HashMap<CardId, RwkvReviewQueueScoreEntry>> {
        // Every deck count asks this for each deck row; most rows score no
        // cards, and the query walks the whole revlog for a deck with few
        // answers (a 3.8M-review history: 20 s per deck list on a PC).
        if cards.is_empty() {
            return Ok(HashMap::new());
        }
        let intervening = self.rwkv_offline_intervening_reviews(deck)?;
        Ok(cards
            .iter()
            .map(|card| {
                (
                    card.card_id,
                    RwkvReviewQueueScoreEntry {
                        retrievability: card.retrievability,
                        intervening_reviews: intervening.get(&card.card_id).copied(),
                        target_retention: card.target_retention,
                    },
                )
            })
            .collect())
    }

    /// For cards among the last `min_intervening_reviews` answers in the
    /// deck's tree: how many answers came after the card's latest one.
    fn rwkv_offline_intervening_reviews(&mut self, deck: &Deck) -> Result<HashMap<CardId, u32>> {
        let limit = self
            .rwkv_offline_deck_config(deck.id)?
            .map(|config| config.inner.rwkv_review_min_intervening_reviews)
            .unwrap_or_default();
        let mut out = HashMap::new();
        if limit == 0 {
            return Ok(out);
        }
        let deck_ids = self.rwkv_offline_deck_tree_ids(deck)?;
        let deck_ids: Vec<_> = deck_ids.iter().map(|id| id.0.to_string()).collect();
        let sql = format!(
            "select r.cid from revlog r join cards c on c.id = r.cid \
             where {RWKV_COUNTED_REVIEW} \
             and (case when c.odid != 0 then c.odid else c.did end) in ({}) \
             order by r.id desc, r.cid desc limit ?",
            deck_ids.join(",")
        );
        let mut stmt = self.storage.db.prepare(&sql)?;
        let rows = stmt.query_map([limit], |row| row.get::<_, i64>(0))?;
        for (index, card_id) in rows.enumerate() {
            out.entry(CardId(card_id?)).or_insert(index as u32);
        }
        Ok(out)
    }

    fn rwkv_offline_deck_tree_ids(&mut self, deck: &Deck) -> Result<HashSet<DeckId>> {
        Ok(self
            .storage
            .deck_id_with_children(deck)?
            .into_iter()
            .collect())
    }

    /// The top-most Instant deck above `deck`, when it uses `deck`'s preset:
    /// its scores (and their batch and refresh options) then stand for
    /// `deck`'s.
    fn rwkv_offline_shared_scope(&mut self, deck: &Deck) -> Result<Option<Deck>> {
        let name = deck.name.as_native_str();
        Ok(self
            .rwkv_offline_instant_scopes()?
            .into_iter()
            .find(|scope| scope.config_id() == deck.config_id() && is_below(name, scope)))
    }

    /// The top-most Instant-enabled decks: each is scored with its children.
    fn rwkv_offline_instant_scopes(&mut self) -> Result<Vec<Deck>> {
        let configs = self.storage.get_deck_config_map()?;
        let mut decks = self.storage.get_all_decks()?;
        decks.sort_by(|a, b| a.name.as_native_str().cmp(b.name.as_native_str()));
        let mut scopes: Vec<Deck> = Vec::new();
        for deck in decks {
            let enabled = deck
                .config_id()
                .and_then(|config_id| configs.get(&config_id))
                .is_some_and(|config| config.inner.rwkv_review_instant_order_enabled);
            let covered = scopes
                .iter()
                .any(|scope| is_below(deck.name.as_native_str(), scope));
            if enabled && !covered {
                scopes.push(deck);
            }
        }
        Ok(scopes)
    }

    /// Install deck count scores for every top-most Instant-enabled deck and
    /// its children, like the desktop's deck browser. Returns the number of
    /// cards scored.
    fn rwkv_offline_install_deck_count_scores_inner(
        &mut self,
        runtime: &mut RwkvOfflineRuntime,
        max_age_secs: i64,
    ) -> Result<usize> {
        let scopes = self.rwkv_offline_instant_scopes()?;
        self.clear_rwkv_deck_count_scores();
        let mut scored = 0;
        for scope in scopes {
            let cards = self.rwkv_offline_scope_scores(runtime, &scope, max_age_secs, false)?;
            scored += cards.len();
            let entries = self.rwkv_offline_score_entries(&scope, &cards)?;
            self.set_rwkv_deck_count_score_entries(scope.id, entries)?;
            // Predictions are shared with the children, but repeat spacing
            // must use each child's own recent answers.
            for child in self.storage.child_decks(&scope)? {
                let tree = self.rwkv_offline_deck_tree_ids(&child)?;
                let child_cards: Vec<_> = cards
                    .iter()
                    .filter(|card| tree.contains(&card.deck_id))
                    .copied()
                    .collect();
                let entries = self.rwkv_offline_score_entries(&child, &child_cards)?;
                self.set_rwkv_deck_count_score_entries(child.id, entries)?;
            }
        }
        Ok(scored)
    }
}

#[cfg(test)]
#[path = "offline_refresh_tests.rs"]
mod refresh_tests;

#[cfg(test)]
mod test {
    use anki_proto::scheduler::card_answer::Rating;
    use anki_proto::scheduler::CardAnswer;
    use anki_proto::scheduler::GetQueuedCardsRequest;

    use super::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::fsrs::preset::AddonFsrsPreset;
    use crate::scheduler::fsrs::preset::AddonFsrsVersion;
    use crate::scheduler::fsrs::preset::FsrsPresetRule;
    use crate::scheduler::states::ReviewState;
    use crate::services::SchedulerService;
    use crate::tests::open_fs_test_collection;
    use crate::tests::CardAdder;
    use crate::tests::DeckAdder;

    #[test]
    fn query_uses_the_same_stable_preset_as_history() {
        let row = Row {
            preset_id: "addon:test:moved".into(),
            ..Default::default()
        };
        assert_eq!(query_input(&row).preset_id, Some(334_249_656_965_035_719));
        let numeric = Row {
            preset_id: "123".into(),
            ..Default::default()
        };
        assert_eq!(query_input(&numeric).preset_id, Some(123));
    }

    fn add_review(col: &mut Collection, card_id: CardId, id: i64) {
        col.transact_no_undo(|col| {
            let _ = col.add_revlog_entry_undoable(RevlogEntry {
                id: RevlogId(id),
                cid: card_id,
                button_chosen: 3,
                review_kind: RevlogReviewKind::Learning,
                ..Default::default()
            })?;
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn synced_reset_mirror_invalidates_the_replay_stamp() -> Result<()> {
        let mut col = Collection::new();
        let card = CardAdder::new().add(&mut col)[0].id;
        let stamp = col.rwkv_offline_replay_stamp()?.unwrap();
        col.set_config_json(
            &format!("rwkvHistoryReset_{}", card.0),
            &serde_json::json!({"after": 1000, "updated": 1}),
            false,
        )?;
        assert_ne!(col.rwkv_offline_replay_stamp()?, Some(stamp));
        Ok(())
    }

    fn answer_next(col: &mut Collection) -> Result<CardId> {
        let queue = SchedulerService::get_queued_cards(
            col,
            GetQueuedCardsRequest {
                fetch_limit: 1,
                ..Default::default()
            },
        )?;
        let card = &queue.cards[0];
        let card_id = CardId(card.card.as_ref().unwrap().id);
        let states = card.states.clone().unwrap();
        let _ = SchedulerService::answer_card(
            col,
            CardAnswer {
                card_id: card_id.0,
                current_state: states.current,
                new_state: states.good,
                rating: Rating::Good as i32,
                answered_at_millis: TimestampMillis::now().0,
                ..Default::default()
            },
        )?;
        Ok(card_id)
    }

    fn test_model() -> OfflineModel {
        OfflineModel {
            path: Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("../qt/aqt/rwkv_inference/sm56/weights.f32.bin"),
            precision: StatePrecision::Int8,
        }
    }

    #[test]
    fn same_day_review_kind_ignores_reviews_before_reset() -> Result<()> {
        let (mut col, _dir) = open_fs_test_collection("rwkv-same-day-reset");
        let card_id = CardAdder::new().add(&mut col)[0].id;
        col.set_due_date(&[card_id], "0", None)?;
        assert_eq!(answer_next(&mut col)?, card_id);
        let history = col.rwkv_historical_review_inputs(Default::default())?;
        let previous = history.reviews[0].review_id;
        col.state.rwkv_offline = Some(Box::new(RwkvOfflineRuntime::load(test_model())?));
        let review = CardState::Normal(NormalState::Review(ReviewState::default()));
        let timing = col.timing_today()?;
        assert_eq!(
            col.rwkv_offline_same_day_review_kind(
                card_id,
                &review,
                true,
                TimestampMillis::now(),
                &timing
            )?,
            Some(3)
        );
        col.set_config_json(
            &format!("rwkvHistoryReset_{}", card_id.0),
            &serde_json::json!({"after": previous, "updated": 1}),
            false,
        )?;
        assert_eq!(
            col.rwkv_offline_same_day_review_kind(
                card_id,
                &review,
                true,
                TimestampMillis::now(),
                &timing
            )?,
            None
        );
        Ok(())
    }

    #[test]
    fn synced_reset_mirror_invalidates_cached_last_review_time() -> Result<()> {
        let (mut col, _dir) = open_fs_test_collection("rwkv-last-review-reset");
        let card_id = CardAdder::new().add(&mut col)[0].id;
        col.set_due_date(&[card_id], "0", None)?;
        assert_eq!(answer_next(&mut col)?, card_id);
        let previous = col
            .rwkv_historical_review_inputs(Default::default())?
            .reviews[0]
            .review_id;
        let mut card = col.all_cards_for_ids(&[card_id], false)?.remove(0);
        card.last_review_time = None;
        let _ = col.update_cards_maybe_undoable(vec![card], true)?;
        let mut cards = col.all_cards_for_ids(&[card_id], false)?;
        col.populate_rwkv_last_review_times(&mut cards)?;
        assert!(cards[0].last_review_time.is_some());

        let key = format!("rwkvHistoryReset_{}", card_id.0);
        col.set_config_json(
            &key,
            &serde_json::json!({"after": previous, "updated": 1}),
            false,
        )?;
        col.populate_rwkv_last_review_times(&mut cards)?;
        assert_eq!(cards[0].last_review_time, None);
        col.set_config_json(&key, &serde_json::json!({"after": 0, "updated": 2}), false)?;
        col.populate_rwkv_last_review_times(&mut cards)?;
        assert!(cards[0].last_review_time.is_some());
        Ok(())
    }

    #[test]
    fn first_answer_after_unlogged_reset_updates_the_resident_model() -> Result<()> {
        let (mut col, _dir) = open_fs_test_collection("rwkv-new-reset-model");
        let first = CardAdder::new().add(&mut col)[0].id;
        let second = CardAdder::new().add(&mut col)[0].id;
        col.set_due_date(&[first, second], "0", None)?;
        let kept = answer_next(&mut col)?;
        let reset = answer_next(&mut col)?;
        assert_ne!(kept, reset);
        let mut card = col.all_cards_for_ids(&[reset], false)?.remove(0);
        card.ctype = CardType::New;
        card.queue = CardQueue::New;
        let _ = col.update_cards_maybe_undoable(vec![card], true)?;
        col.update_default_deck_config(|config| {
            config.rwkv_review_enabled = true;
            config.rwkv_review_instant_order_enabled = true;
        });
        let mut runtime = RwkvOfflineRuntime::load(test_model())?;
        col.rwkv_offline_sync_history(&mut runtime)?;
        assert_eq!(runtime.identity.as_ref().unwrap().review_count, 1);
        col.state.rwkv_offline = Some(Box::new(runtime));
        assert_eq!(answer_next(&mut col)?, reset);
        let history = col.rwkv_historical_review_inputs(Default::default())?;
        let runtime = col.state.rwkv_offline.as_ref().unwrap();
        assert_eq!(runtime.identity.as_ref().unwrap().review_count, 2);
        assert_eq!(
            runtime.identity.as_ref().unwrap(),
            history
                .metadata
                .as_ref()
                .unwrap()
                .identity
                .as_ref()
                .unwrap()
        );
        Ok(())
    }

    #[test]
    fn subdeck_queues_share_their_instant_parent_scores() -> Result<()> {
        let mut col = Collection::new();
        let parent = DeckAdder::new("parent")
            .with_config(|config| config.inner.rwkv_review_instant_order_enabled = true)
            .add(&mut col);
        let mut child = DeckAdder::new("parent::child").add(&mut col);
        child.normal_mut()?.config_id = parent.config_id().unwrap().0;
        col.add_or_update_deck(&mut child)?;
        let other = DeckAdder::new("parent::other")
            .with_config(|config| config.inner.rwkv_review_instant_order_enabled = true)
            .add(&mut col);
        let shared = |col: &mut Collection, deck: &Deck| {
            col.rwkv_offline_shared_scope(deck)
                .unwrap()
                .map(|scope| scope.id)
        };
        assert_eq!(shared(&mut col, &child), Some(parent.id));
        // a top-most Instant deck is its own scope
        assert_eq!(shared(&mut col, &parent), None);
        // another preset under the parent is scored on its own
        assert_eq!(shared(&mut col, &other), None);
        Ok(())
    }

    #[test]
    fn expired_deck_counts_rescore_only_cards_near_their_target() {
        let scored = |id, retrievability| ScoredCard {
            card_id: CardId(id),
            deck_id: DeckId(1),
            retrievability,
            target_retention: Some(0.9),
        };
        let row = |id| Row {
            card_id: id,
            current_elapsed_seconds: Some(3 * 86_400),
            ..Default::default()
        };
        let previous = [
            scored(1, 0.5),  // far below its target: still due
            scored(2, 0.89), // near it: may have crossed
            scored(3, 0.98), // far above: still not due
            scored(4, 0.5),  // answered since
            scored(5, 0.5),  // no longer in the scope (moved, suspended)
            scored(7, 0.5),  // answered an hour ago: still moving fast
        ];
        let mut recent = row(7);
        recent.current_elapsed_seconds = Some(3600);
        let rows = vec![row(1), row(2), row(3), row(4), row(6), recent];
        let (keep, rescore) = split_near_target(&previous, &[CardId(4)], rows);
        let kept: Vec<_> = keep.iter().map(|card| card.card_id.0).collect();
        assert_eq!(kept, vec![1, 3]);
        // 6: new to the scope
        let rescored: Vec<_> = rescore.iter().map(|row| row.card_id).collect();
        assert_eq!(rescored, vec![2, 4, 6, 7]);
    }

    #[test]
    fn replay_stamp_changes_only_with_what_the_replay_reads() -> Result<()> {
        let mut col = Collection::new();
        let deck = DeckAdder::new("deck").add(&mut col);
        let other = DeckAdder::new("other").with_config(|_| {}).add(&mut col);
        let card = CardAdder::new().deck(deck.id).add(&mut col)[0].id;
        add_review(&mut col, card, 1_000_000);
        let stamp = col.rwkv_offline_replay_stamp()?.unwrap();

        // deck options that the replay doesn't read
        let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        config.inner.learn_steps = vec![2.0, 20.0];
        col.add_or_update_deck_config(&mut config)?;
        assert_eq!(col.rwkv_offline_replay_stamp()?, Some(stamp));

        // an answer: what absorbing it assumes
        add_review(&mut col, card, 2_000_000);
        let stamp = stamp.appended(2_000_000);
        assert_eq!(col.rwkv_offline_replay_stamp()?, Some(stamp));

        // a card moved to a deck with another preset
        col.set_deck(&[card], other.id)?;
        let moved = col.rwkv_offline_replay_stamp()?.unwrap();
        assert_ne!(moved, stamp);

        // a deck switched to another preset
        let mut deck = col.storage.get_deck(deck.id)?.unwrap();
        deck.normal_mut()?.config_id = other.config_id().unwrap().0;
        col.add_or_update_deck(&mut deck)?;
        let switched = col.rwkv_offline_replay_stamp()?.unwrap();
        assert_ne!(switched, moved);

        // "Next day starts at"
        col.set_v2_rollover(9)?;
        assert_ne!(col.rwkv_offline_replay_stamp()?.unwrap(), switched);

        // Undo removes an added review and changes the replay stamp again.
        let before_answer = col.rwkv_offline_replay_stamp()?;
        col.transact(Op::AnswerCard, |col| {
            let _ = col.add_revlog_entry_undoable(RevlogEntry {
                id: RevlogId(3_000_000),
                cid: card,
                button_chosen: 3,
                review_kind: RevlogReviewKind::Learning,
                ..Default::default()
            })?;
            Ok(())
        })?;
        assert_ne!(col.rwkv_offline_replay_stamp()?, before_answer);
        let _ = col.undo()?;
        assert_eq!(col.rwkv_offline_replay_stamp()?, before_answer);

        // preset overlay rules match cards by search: always the full check
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:x".into(),
                    name: "x".into(),
                    fsrs_version: AddonFsrsVersion::Six,
                    params: vec![1.0; 21],
                    desired_retention: 0.9,
                    historical_retention: 0.9,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "deck:other".into(),
                    preset_id: "addon:x".into(),
                }],
                ..Default::default()
            },
        )?;
        assert_eq!(col.rwkv_offline_replay_stamp()?, None);
        Ok(())
    }
}
