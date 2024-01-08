// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::iter;
use std::path::Path;
use std::thread;
use std::time::Duration;

use anki_io::write_file;
use anki_proto::scheduler::ComputeFsrsWeightsResponse;
use anki_proto::stats::revlog_entry;
use anki_proto::stats::RevlogEntries;
use fsrs::CombinedProgressState;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::ModelEvaluation;
use fsrs::FSRS;
use itertools::Itertools;
use prost::Message;

use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::search::Node;
use crate::search::SearchNode;
use crate::search::SortMode;

pub(crate) type Weights = Vec<f32>;

impl Collection {
    /// Note this does not return an error if there are less than 1000 items -
    /// the caller should instead check the fsrs_items count in the return
    /// value.
    pub fn compute_weights(
        &mut self,
        search: &str,
        current_preset: u32,
        total_presets: u32,
    ) -> Result<ComputeFsrsWeightsResponse> {
        let mut anki_progress = self.new_progress_handler::<ComputeWeightsProgress>();
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        if revlogs.len() < 1000 {
            return Err(AnkiError::FsrsInsufficientReviews {
                count: revlogs.len(),
            });
        }
        let items = fsrs_items_for_training(revlogs, timing.next_day_at);
        let fsrs_items = items.len() as u32;
        anki_progress.update(false, |p| {
            p.fsrs_items = fsrs_items;
            p.current_preset = current_preset;
            p.total_presets = total_presets;
        })?;
        // adapt the progress handler to our built-in progress handling
        let progress = CombinedProgressState::new_shared();
        let progress2 = progress.clone();
        thread::spawn(move || {
            let mut finished = false;
            while !finished {
                thread::sleep(Duration::from_millis(100));
                let mut guard = progress.lock().unwrap();
                if let Err(_err) = anki_progress.update(false, |s| {
                    s.total_iterations = guard.total() as u32;
                    s.current_iteration = guard.current() as u32;
                    finished = guard.finished();
                }) {
                    guard.want_abort = true;
                    return;
                }
            }
        });
        let fsrs = FSRS::new(None)?;
        let weights = fsrs.compute_weights(items, Some(progress2))?;
        Ok(ComputeFsrsWeightsResponse {
            weights,
            fsrs_items,
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
    pub fn export_revlog_entries_to_protobuf(
        &mut self,
        min_entries: usize,
        target_path: &Path,
    ) -> Result<()> {
        let entries = self.storage.get_all_revlog_entries_in_card_order()?;
        if entries.len() < min_entries {
            return Err(AnkiError::FsrsInsufficientData);
        }
        let entries = entries.into_iter().map(revlog_entry_to_proto).collect_vec();
        let next_day_at = self.timing_today()?.next_day_at.0;
        let entries = RevlogEntries {
            entries,
            next_day_at,
        };
        let data = entries.encode_to_vec();
        write_file(target_path, data)?;
        Ok(())
    }

    pub fn evaluate_weights(&mut self, weights: &Weights, search: &str) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let mut anki_progress = self.new_progress_handler::<ComputeWeightsProgress>();
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        if revlogs.len() < 1000 {
            return Err(AnkiError::FsrsInsufficientReviews {
                count: revlogs.len(),
            });
        }
        anki_progress.state.fsrs_items = revlogs.len() as u32;
        let items = fsrs_items_for_training(revlogs, timing.next_day_at);
        let fsrs = FSRS::new(Some(weights))?;
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
pub struct ComputeWeightsProgress {
    pub current_iteration: u32,
    pub total_iterations: u32,
    pub fsrs_items: u32,
    /// Only used in 'compute all weights' case
    pub current_preset: u32,
    /// Only used in 'compute all weights' case
    pub total_presets: u32,
}

/// Convert a series of revlog entries sorted by card id into FSRS items.
fn fsrs_items_for_training(revlogs: Vec<RevlogEntry>, next_day_at: TimestampSecs) -> Vec<FSRSItem> {
    let mut revlogs = revlogs
        .into_iter()
        .group_by(|r| r.cid)
        .into_iter()
        .filter_map(|(_cid, entries)| {
            single_card_revlog_to_items(entries.collect(), next_day_at, true)
        })
        .flat_map(|i| i.0)
        .collect_vec();
    revlogs.sort_by_cached_key(|r| r.reviews.len());
    revlogs
}

/// Transform the revlog history for a card into a list of FSRSItems. FSRS
/// expects multiple items for a given card when training - for revlog
/// `[1,2,3]`, we create FSRSItems corresponding to `[1,2]` and `[1,2,3]`
/// in training, and `[1]`, [1,2]` and `[1,2,3]` when calculating memory
/// state.
///
/// Returns (items, revlog_complete), the latter of which is assumed
/// when the revlogs have a learning step, or start with manual scheduling. When
/// revlogs are incomplete, the starting difficulty is later inferred from the
/// SM2 data, instead of using the standard FSRS initial difficulty.
pub(crate) fn single_card_revlog_to_items(
    mut entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
) -> Option<(Vec<FSRSItem>, bool)> {
    let mut last_learn_entry = None;
    let mut revlogs_complete = false;
    for (index, entry) in entries.iter().enumerate().rev() {
        if matches!(
            (entry.review_kind, entry.button_chosen),
            (RevlogReviewKind::Learning, 1..=4)
        ) {
            last_learn_entry = Some(index);
            revlogs_complete = true;
        } else if last_learn_entry.is_some() {
            break;
        }
    }
    if !revlogs_complete {
        revlogs_complete = matches!(
            entries.first(),
            Some(RevlogEntry {
                review_kind: RevlogReviewKind::Manual,
                ..
            })
        );
    }
    let first_relearn = entries
        .iter()
        .enumerate()
        .find(|(_idx, e)| e.review_kind == RevlogReviewKind::Relearning)
        .map(|(idx, _)| idx);
    if let Some(idx) = last_learn_entry.or(first_relearn) {
        // start from the (re)learning step
        if idx > 0 {
            entries.drain(..idx);
        }
    } else if training {
        // when training, we ignore cards that don't have any learning steps
        return None;
    }

    // Filter out unwanted entries
    let mut unique_dates = std::collections::HashSet::new();
    entries.retain(|entry| {
        let manually_rescheduled =
            entry.review_kind == RevlogReviewKind::Manual || entry.button_chosen == 0;
        let cram = entry.review_kind == RevlogReviewKind::Filtered && entry.ease_factor == 0;
        if manually_rescheduled || cram {
            return false;
        }
        // Keep only the first review when multiple reviews done on one day
        unique_dates.insert(entry.days_elapsed(next_day_at))
    });

    // Compute delta_t for each entry
    let delta_ts = iter::once(0)
        .chain(entries.iter().tuple_windows().map(|(previous, current)| {
            previous.days_elapsed(next_day_at) - current.days_elapsed(next_day_at)
        }))
        .collect_vec();

    let skip = if training { 1 } else { 0 };
    // Convert the remaining entries into separate FSRSItems, where each item
    // contains all reviews done until then.
    let items = entries
        .iter()
        .enumerate()
        .skip(skip)
        .map(|(outer_idx, _)| {
            let reviews = entries
                .iter()
                .take(outer_idx + 1)
                .enumerate()
                .map(|(inner_idx, r)| FSRSReview {
                    rating: r.button_chosen as u32,
                    delta_t: delta_ts[inner_idx],
                })
                .collect();
            FSRSItem { reviews }
        })
        .collect_vec();
    if items.is_empty() {
        None
    } else {
        Some((items, revlogs_complete))
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
        } as i32,
    }
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;

    const NEXT_DAY_AT: TimestampSecs = TimestampSecs(86400 * 100);

    pub(crate) fn revlog(review_kind: RevlogReviewKind, days_ago: i64) -> RevlogEntry {
        RevlogEntry {
            review_kind,
            id: ((NEXT_DAY_AT.0 - days_ago * 86400) * 1000).into(),
            button_chosen: 3,
            ..Default::default()
        }
    }

    pub(crate) fn review(delta_t: u32) -> FSRSReview {
        FSRSReview { rating: 3, delta_t }
    }

    pub(crate) fn convert(revlog: &[RevlogEntry], training: bool) -> Option<Vec<FSRSItem>> {
        single_card_revlog_to_items(revlog.to_vec(), NEXT_DAY_AT, training).map(|i| i.0)
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

    pub(crate) use fsrs_items;

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
        assert_eq!(
            convert(
                &[
                    revlog(RevlogReviewKind::Learning, 10),
                    revlog(RevlogReviewKind::Review, 9),
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
}
