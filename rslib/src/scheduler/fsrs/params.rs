// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;
use std::iter;
use std::path::Path;
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
use fsrs::CombinedProgressState;
use fsrs::ComputeParametersInput;
use fsrs::ComputeParametersVersion;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::ModelEvaluation;
use fsrs::ModelVersion;
use fsrs::TrainingConfig;
use fsrs::FSRS;
use itertools::Itertools;
use prost::Message;

use crate::decks::immediate_parent_name;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::search::Node;
use crate::search::SearchNode;
use crate::search::SortMode;

pub(crate) type Params = Vec<f32>;

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
}

pub(crate) struct PreparedComputeParams {
    pub current_params: Params,
    pub num_of_relearning_steps: usize,
    pub items: Vec<FSRSItem>,
    pub card_ids: Vec<i64>,
    pub target_counts: TrainingTargetCounts,
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

fn training_target_counts_from_items(items: &[FSRSItem]) -> TrainingTargetCounts {
    let long_term_targets = items
        .iter()
        .filter(|item| {
            item.reviews
                .last()
                .is_some_and(|review| review.delta_t >= 1.0)
        })
        .count();
    TrainingTargetCounts {
        total_targets: items.len(),
        long_term_targets,
        short_term_targets: items.len().saturating_sub(long_term_targets),
    }
}

fn health_check_passed(eval: ModelEvaluation, items: &[FSRSItem]) -> bool {
    let item_count = items.len() as u32;
    if item_count == 0 {
        return false;
    }
    let retention = items.iter().fold(0, |passed, item| {
        passed + (item.reviews.last().map(|review| review.rating).unwrap_or(0) > 1) as u32
    }) as f32
        / item_count as f32;
    let adjusted_log_loss = eval.log_loss / log_loss_adjustment(retention);
    let adjusted_rmse = eval.rmse_bins / rmse_adjustment(retention, item_count);
    adjusted_log_loss <= 1.11 || adjusted_rmse <= 1.53
}

/// Below this many items, fsrs-rs returns its default or initial parameters
/// without training.
const MIN_ITEMS_FOR_TRAINING: usize = 64;

/// Keep the current parameters unless the optimized ones predict the training
/// items better. The log loss of two FSRS-7 parameter sets on the same items
/// is a fair comparison: the scheduling penalties are part of the training
/// objective only. Parameters of an older model cannot be evaluated on these
/// items, because their elapsed time is in whole days, so they are kept only
/// when there are too few items to train.
fn params_to_keep(current: &[f32], optimized: Params, items: &[FSRSItem]) -> Result<Params> {
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
    let current_log_loss = current_fsrs.evaluate(items.to_vec(), |_| true)?.log_loss;
    let optimized_log_loss = FSRS::new(&optimized)?
        .evaluate(items.to_vec(), |_| true)?
        .log_loss;
    Ok(if current_log_loss <= optimized_log_loss {
        current.to_vec()
    } else {
        optimized
    })
}

pub(crate) fn compute_params_from_prepared(
    prepared: PreparedComputeParams,
    progress: Option<Arc<Mutex<CombinedProgressState>>>,
    run_health_check: bool,
) -> Result<ComputeFsrsParamsResponse> {
    let PreparedComputeParams {
        current_params,
        num_of_relearning_steps,
        items,
        card_ids,
        target_counts: _,
    } = prepared;
    let fsrs_items = items.len() as u32;
    if fsrs_items == 0 {
        return Ok(ComputeFsrsParamsResponse {
            params: current_params,
            fsrs_items,
            health_check_passed: None,
        });
    }

    let run_health_check = run_health_check && items.len() > 300;
    let health_check_card_ids = run_health_check.then(|| card_ids.clone());
    let params = fsrs::compute_parameters(ComputeParametersInput {
        train_set: items.clone(),
        card_ids: Some(card_ids),
        progress,
        enable_short_term: true,
        enable_sched_penalties: true,
        model_version: ComputeParametersVersion::Fsrs7,
        num_relearning_steps: Some(num_of_relearning_steps),
        training_config: Some(TrainingConfig {
            num_epochs: 8,
            ..Default::default()
        }),
    })?;
    let params = params_to_keep(&current_params, params, &items)?;
    let health_check_passed = if run_health_check {
        fsrs::evaluate_with_time_series_splits(
            ComputeParametersInput {
                train_set: items.clone(),
                card_ids: health_check_card_ids,
                progress: None,
                enable_short_term: true,
                enable_sched_penalties: true,
                model_version: ComputeParametersVersion::Fsrs7,
                num_relearning_steps: Some(num_of_relearning_steps),
                training_config: Some(TrainingConfig {
                    num_epochs: 8,
                    ..Default::default()
                }),
            },
            |_| true,
        )
        .ok()
        .map(|evaluation| health_check_passed(evaluation, &items))
    } else {
        None
    };

    Ok(ComputeFsrsParamsResponse {
        params,
        fsrs_items,
        health_check_passed,
    })
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
        } = request;

        self.clear_progress();
        let prepared = self.prepare_compute_params(
            search,
            ignore_revlogs_before,
            current_params,
            num_of_relearning_steps,
        )?;
        if prepared.items.is_empty() {
            return Ok(ComputeFsrsParamsResponse {
                params: current_params.to_vec(),
                fsrs_items: 0,
                health_check_passed: None,
            });
        }
        let target_count = prepared.target_counts.total_targets as u32;

        let create_progress_thread = || -> Result<_> {
            let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
            anki_progress.update(false, |p| {
                p.current_preset = current_preset;
                p.total_presets = total_presets;
            })?;
            let progress = CombinedProgressState::new_shared();
            let progress2 = progress.clone();
            let progress_thread = thread::spawn(move || {
                let mut finished = false;
                while !finished {
                    thread::sleep(Duration::from_millis(100));
                    let mut guard = progress.lock().unwrap();
                    if let Err(_err) = anki_progress.update(false, |s| {
                        s.total_iterations = guard.total() as u32;
                        s.current_iteration = guard.current() as u32;
                        s.reviews = target_count;
                        finished = guard.finished();
                    }) {
                        guard.want_abort = true;
                        return;
                    }
                }
            });
            Ok((progress2, progress_thread))
        };

        let (progress, progress_thread) = create_progress_thread()?;
        let output = compute_params_from_prepared(prepared, Some(progress), health_check);
        progress_thread.join().ok();
        output
    }

    pub(crate) fn prepare_compute_params(
        &mut self,
        search: &str,
        ignore_revlogs_before: TimestampMillis,
        current_params: &[f32],
        num_of_relearning_steps: usize,
    ) -> Result<PreparedComputeParams> {
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        let (items, card_ids, _) =
            fsrs_items_for_training(revlogs, timing.next_day_at, ignore_revlogs_before, true);
        let target_counts = training_target_counts_from_items(&items);
        Ok(PreparedComputeParams {
            current_params: current_params.to_vec(),
            num_of_relearning_steps,
            items,
            card_ids,
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

    pub fn evaluate_params(
        &mut self,
        search: &str,
        ignore_revlogs_before: TimestampMillis,
        num_of_relearning_steps: usize,
    ) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        let (items, card_ids, review_count) =
            fsrs_items_for_training(revlogs, timing.next_day_at, ignore_revlogs_before, true);
        let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
        anki_progress.state.reviews = review_count as u32;
        let input = ComputeParametersInput {
            train_set: items.clone(),
            card_ids: Some(card_ids),
            progress: None,
            enable_short_term: true,
            enable_sched_penalties: true,
            model_version: ComputeParametersVersion::Fsrs7,
            num_relearning_steps: Some(num_of_relearning_steps),
            training_config: Some(TrainingConfig {
                num_epochs: 8,
                ..Default::default()
            }),
        };
        Ok(fsrs::evaluate_with_time_series_splits(input, |ip| {
            anki_progress
                .update(false, |p| {
                    p.total_iterations = ip.total as u32;
                    p.current_iteration = ip.current as u32;
                })
                .is_ok()
        })?)
    }

    pub fn evaluate_params_legacy(
        &mut self,
        params: &Params,
        search: &str,
        ignore_revlogs_before: TimestampMillis,
    ) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let mut anki_progress = self.new_progress_handler::<ComputeParamsProgress>();
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let revlogs: Vec<RevlogEntry> = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let fsrs = FSRS::new(params)?;
        // Older models count elapsed time in whole days, so they get no
        // same-day targets, as in their training.
        let (items, _, review_count) = fsrs_items_for_training(
            revlogs,
            timing.next_day_at,
            ignore_revlogs_before,
            fsrs.version() == ModelVersion::Fsrs7,
        );
        anki_progress.state.reviews = review_count as u32;
        Ok(fsrs.evaluate(items, |ip| {
            anki_progress
                .update(false, |p| {
                    p.total_iterations = ip.total as u32;
                    p.current_iteration = ip.current as u32;
                })
                .is_ok()
        })?)
    }
}

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeParamsProgress {
    pub current_iteration: u32,
    pub total_iterations: u32,
    pub reviews: u32,
    /// Only used in 'compute all params' case
    pub current_preset: u32,
    /// Only used in 'compute all params' case
    pub total_presets: u32,
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
    pub failed: bool,
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
) -> (Vec<FSRSItem>, Vec<i64>, usize) {
    let mut review_count: usize = 0;
    let mut revlogs = revlogs
        .into_iter()
        .chunk_by(|r| r.cid)
        .into_iter()
        .filter_map(|(cid, entries)| {
            reviews_for_fsrs(
                entries.collect(),
                next_day_at,
                true,
                review_revlogs_before,
                include_same_day,
            )
            .map(|reviews| (cid, reviews))
        })
        .flat_map(|(cid, reviews)| {
            review_count += reviews.filtered_revlogs.len();

            reviews
                .fsrs_items
                .into_iter()
                .map(move |(revlog_id, item)| (revlog_id, cid, item))
        })
        .collect_vec();
    // Sort by RevlogId
    revlogs.sort_by_key(|(revlog_id, _, _)| revlog_id.0);
    // Extract FSRSItems and card ids after sorting, preserving alignment.
    let (card_ids, items) = revlogs
        .into_iter()
        .map(|(_, cid, item)| (cid.0, item))
        .unzip();
    (items, card_ids, review_count)
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

/// Filter out unwanted revlog entries, then create a series of FSRS items for
/// training/memory state calculation.
///
/// Filtering consists of removing revlog entries before the supplied timestamp,
/// and removing items such as reviews that happened prior to a card being reset
/// to new.
pub(crate) fn reviews_for_fsrs(
    mut entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
    ignore_revlogs_before: TimestampMillis,
    include_same_day: bool,
) -> Option<ReviewsForFsrs> {
    let mut first_of_last_learn_entries = None;
    let mut first_user_grade_idx = None;
    let mut revlogs_complete = false;
    // Working backwards from the latest review...
    for (index, entry) in entries.iter().enumerate().rev() {
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
                return None;
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
                return None;
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
        return None;
    } else {
        // if no valid user grades were found, ignore the card.
        let idx = first_user_grade_idx?;
        // if there are no learning entries, but the user has reviewed the card,
        // we ignore all entries before the first grade
        if idx > 0 {
            entries.drain(..idx);
        }
    }

    // Filter out unwanted entries
    entries.retain(|entry| entry.has_rating_and_affects_scheduling());

    // Compute delta_t for each entry
    let delta_ts = iter::once(0.0)
        .chain(entries.iter().tuple_windows().map(|(previous, current)| {
            if include_same_day {
                let elapsed_ms = current.id.0.saturating_sub(previous.id.0);
                if elapsed_ms > 0 {
                    (elapsed_ms as f32 / 86_400_000.0).max(1.0 / 86_400.0)
                } else {
                    0.0
                }
            } else {
                (previous.days_elapsed(next_day_at) - current.days_elapsed(next_day_at)) as f32
            }
        }))
        .collect_vec();

    let items = if training {
        // Convert the remaining entries into separate FSRSItems, where each item
        // contains all reviews done until then.
        let mut items = Vec::with_capacity(entries.len());
        let mut current_reviews = Vec::with_capacity(entries.len());
        for (idx, (entry, &delta_t)) in entries.iter().zip(delta_ts.iter()).enumerate() {
            current_reviews.push(FSRSReview {
                rating: entry.button_chosen as u32,
                delta_t,
            });
            if idx >= 1 && delta_t > 0.0 {
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
        None
    } else {
        Some(ReviewsForFsrs {
            fsrs_items: items,
            revlogs_complete,
            filtered_revlogs: entries,
        })
    }
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
    use super::*;

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

    pub(crate) fn review(delta_t: u32) -> FSRSReview {
        FSRSReview {
            rating: 3,
            delta_t: delta_t as f32,
        }
    }

    pub(crate) fn convert_ignore_before(
        revlog: &[RevlogEntry],
        training: bool,
        ignore_before: TimestampMillis,
    ) -> Option<Vec<FSRSItem>> {
        reviews_for_fsrs(revlog.to_vec(), NEXT_DAY_AT, training, ignore_before, false)
            .map(|i| i.fsrs_items.into_iter().map(|(_, item)| item).collect_vec())
    }

    pub(crate) fn convert(revlog: &[RevlogEntry], training: bool) -> Option<Vec<FSRSItem>> {
        convert_ignore_before(revlog, training, 0.into())
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
    fn fsrs7_training_preserves_fractional_same_day_elapsed_time() {
        let mut first = revlog(RevlogReviewKind::Learning, 1);
        first.id.0 += 10 * 60 * 60 * 1000;
        let mut second = revlog(RevlogReviewKind::Learning, 1);
        second.id.0 = first.id.0 + 10 * 60 * 1000;

        let output =
            reviews_for_fsrs(vec![first, second], NEXT_DAY_AT, true, 0.into(), true).unwrap();
        let delta_t = output.fsrs_items[0].1.reviews[1].delta_t;
        assert!((delta_t - 10.0 / (24.0 * 60.0)).abs() < 1e-6);
    }

    #[test]
    fn card_ids_align_with_sorted_training_items() {
        let (items, card_ids, review_count) = fsrs_items_for_training(
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

        assert_eq!(card_ids, vec![2, 1, 1]);
        assert_eq!(
            items.iter().map(|item| item.reviews.len()).collect_vec(),
            vec![2, 2, 3]
        );
        assert_eq!(review_count, 5);
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

    fn items_failed_after(days: f32, count: usize) -> Vec<FSRSItem> {
        vec![
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
        ]
    }

    #[test]
    fn optimize_keeps_current_fsrs7_params_when_the_new_ones_fit_worse() -> Result<()> {
        // Every card is forgotten after 30 days, so a high initial stability
        // for Good fits worse than the defaults.
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

    #[test]
    fn legacy_evaluate_gives_older_models_no_same_day_targets() -> Result<()> {
        let mut col = Collection::new();
        let note = crate::tests::NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id])?[0];
        let start = TimestampMillis::now().0 - 5 * 86_400_000;
        for (offset, button_chosen, review_kind) in [
            (0, 3, RevlogReviewKind::Learning),
            (10 * 60_000, 1, RevlogReviewKind::Learning),
            (3 * 86_400_000, 3, RevlogReviewKind::Review),
        ] {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(start + offset),
                    cid,
                    button_chosen,
                    review_kind,
                    interval: 1,
                    ..Default::default()
                },
                false,
            )?;
        }

        // FSRS-6 rounds a same-day elapsed time to 0 and predicts R = 1, so
        // the failed step would add about 9 nats.
        let log_loss = col
            .evaluate_params_legacy(&fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec(), "", 0.into())?
            .log_loss;
        assert!(log_loss < 1.0, "{log_loss}");
        Ok(())
    }
}
