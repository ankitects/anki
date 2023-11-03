// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::iter;
use std::thread;
use std::time::Duration;

use anki_proto::scheduler::ComputeFsrsWeightsResponse;
use fsrs::CombinedProgressState;
use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::ModelEvaluation;
use fsrs::FSRS;
use itertools::Itertools;

use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::search::SortMode;

pub(crate) type Weights = Vec<f32>;

impl Collection {
    pub fn compute_weights(&mut self, search: &str) -> Result<ComputeFsrsWeightsResponse> {
        let mut anki_progress = self.new_progress_handler::<ComputeWeightsProgress>();
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        let items = fsrs_items_for_training(revlogs, timing.next_day_at);
        let fsrs_items = items.len() as u32;
        anki_progress.update(false, |p| p.fsrs_items = fsrs_items)?;
        // adapt the progress handler to our built-in progress handling
        let progress = CombinedProgressState::new_shared();
        let progress2 = progress.clone();
        thread::spawn(move || {
            let mut finished = false;
            while !finished {
                thread::sleep(Duration::from_millis(100));
                let mut guard = progress.lock().unwrap();
                if let Err(_err) = anki_progress.update(false, |s| {
                    s.total = guard.total() as u32;
                    s.current = guard.current() as u32;
                    finished = s.total > 0 && s.total == s.current;
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
        self.search_cards_into_table(search, SortMode::NoOrder)?
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_order()
    }

    pub fn evaluate_weights(&mut self, weights: &Weights, search: &str) -> Result<ModelEvaluation> {
        let timing = self.timing_today()?;
        let mut anki_progress = self.new_progress_handler::<ComputeWeightsProgress>();
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_order()?;
        anki_progress.state.fsrs_items = revlogs.len() as u32;
        let items = fsrs_items_for_training(revlogs, timing.next_day_at);
        let fsrs = FSRS::new(Some(weights))?;
        Ok(fsrs.evaluate(items, |ip| {
            anki_progress
                .update(false, |p| {
                    p.total = ip.total as u32;
                    p.current = ip.current as u32;
                })
                .is_ok()
        })?)
    }
}

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeWeightsProgress {
    pub current: u32,
    pub total: u32,
    pub fsrs_items: u32,
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
/// state. Returns (items, found_learn_entry), the latter of which is used
/// to determine whether the revlogs have been truncated when not training.
pub(crate) fn single_card_revlog_to_items(
    mut entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    training: bool,
) -> Option<(Vec<FSRSItem>, bool)> {
    let mut last_learn_entry = None;
    let mut found_learn_entry = false;
    for (index, entry) in entries.iter().enumerate().rev() {
        if matches!(
            (entry.review_kind, entry.button_chosen),
            (RevlogReviewKind::Learning, 1..=4)
        ) {
            last_learn_entry = Some(index);
            found_learn_entry = true;
        } else if last_learn_entry.is_some() {
            break;
        }
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

    // Old versions of Anki did not record Manual entries in the review log when
    // cards were manually rescheduled. So we look for times when the card has
    // gone from Review to Learning, indicating it has been reset, and remove
    // entries after.
    for (i, (a, b)) in entries.iter().tuple_windows().enumerate() {
        if let (
            RevlogReviewKind::Review | RevlogReviewKind::Relearning,
            RevlogReviewKind::Learning,
        ) = (a.review_kind, b.review_kind)
        {
            // Remove entry and all following
            entries.truncate(i + 1);
            break;
        }
    }

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
        Some((items, found_learn_entry))
    }
}

impl RevlogEntry {
    fn days_elapsed(&self, next_day_at: TimestampSecs) -> u32 {
        (next_day_at.elapsed_secs_since(self.id.as_secs()) / 86_400).max(0) as u32
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
