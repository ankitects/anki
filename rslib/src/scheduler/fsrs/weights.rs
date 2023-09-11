// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::iter;
use std::thread;
use std::time::Duration;

use fsrs::FSRSItem;
use fsrs::FSRSReview;
use fsrs::ModelEvaluation;
use fsrs::ProgressState;
use fsrs::FSRS;
use itertools::Itertools;

use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::search::SortMode;

pub(crate) type Weights = Vec<f32>;

impl Collection {
    pub fn compute_weights(&mut self, search: &str) -> Result<Vec<f32>> {
        let timing = self.timing_today()?;
        let revlogs = self.revlog_for_srs(search)?;
        let revlog_count = revlogs.len();
        let items = fsrs_items_for_training(revlogs, timing.next_day_at);
        let mut anki_progress = self.new_progress_handler::<ComputeWeightsProgress>();
        anki_progress.state.revlog_entries = revlog_count as u32;
        // adapt the progress handler to our built-in progress handling
        let progress = ProgressState::new_shared();
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
        fsrs.compute_weights(items, Some(progress2))
            .map_err(Into::into)
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
        anki_progress.state.revlog_entries = revlogs.len() as u32;
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
    pub revlog_entries: u32,
}

/// Convert a series of revlog entries sorted by card id into FSRS items.
fn fsrs_items_for_training(revlogs: Vec<RevlogEntry>, next_day_at: TimestampSecs) -> Vec<FSRSItem> {
    let mut revlogs = revlogs
        .into_iter()
        .group_by(|r| r.cid)
        .into_iter()
        .filter_map(|(_cid, entries)| single_card_revlog_to_items(entries.collect(), next_day_at))
        .flatten()
        .collect_vec();
    revlogs.sort_by_cached_key(|r| r.reviews.len());
    revlogs
}

/// When updating memory state, FSRS only requires the last FSRSItem that
/// contains the full history.
pub(super) fn fsrs_items_for_memory_state(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
) -> Vec<(CardId, FSRSItem)> {
    let mut out = vec![];
    for (card_id, group) in revlogs.into_iter().group_by(|r| r.cid).into_iter() {
        let entries = group.into_iter().collect_vec();
        if let Some(mut items) = single_card_revlog_to_items(entries, next_day_at) {
            if let Some(item) = items.pop() {
                out.push((card_id, item));
            }
        }
    }
    out
}

/// Transform the revlog history for a card into a list of FSRSItems. FSRS
/// expects multiple items for a given card when training - for revlog
/// `[1,2,3]`, we create FSRSItems corresponding to `[1]`, `[1,2]`, `[1,2,3]`.
fn single_card_revlog_to_items(
    mut entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
) -> Option<Vec<FSRSItem>> {
    // Find the index of the first learn entry in the last continuous group
    let mut index_to_keep = 0;
    let mut i = entries.len();

    while i > 0 {
        i -= 1;
        if entries[i].review_kind == RevlogReviewKind::Learning {
            index_to_keep = i;
        } else if index_to_keep != 0 {
            // Found a continuous group
            break;
        }
    }

    // Remove all entries before this one
    entries.drain(..index_to_keep);

    // we ignore cards that don't start in the learning state
    if let Some(entry) = entries.first() {
        if entry.review_kind != RevlogReviewKind::Learning {
            return None;
        }
    } else {
        // no revlog entries
        return None;
    }

    // Keep only the first review when multiple reviews done on one day
    let mut unique_dates = std::collections::HashSet::new();
    entries.retain(|entry| unique_dates.insert(entry.days_elapsed(next_day_at)));

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

    // Skip the first learning step, then convert the remaining entries into
    // separate FSRSItems, where each item contains all reviews done until then.
    Some(
        entries
            .iter()
            .enumerate()
            .skip(1)
            .map(|(outer_idx, _)| {
                let reviews = entries
                    .iter()
                    .take(outer_idx + 1)
                    .enumerate()
                    .map(|(inner_idx, r)| FSRSReview {
                        rating: r.button_chosen as i32,
                        delta_t: delta_ts[inner_idx] as i32,
                    })
                    .collect();
                FSRSItem { reviews }
            })
            .collect(),
    )
}

impl RevlogEntry {
    fn days_elapsed(&self, next_day_at: TimestampSecs) -> u32 {
        (next_day_at.elapsed_secs_since(self.id.as_secs()) / 86_400).max(0) as u32
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const NEXT_DAY_AT: TimestampSecs = TimestampSecs(86400 * 100);

    fn revlog(review_kind: RevlogReviewKind, days_ago: i64) -> RevlogEntry {
        RevlogEntry {
            review_kind,
            id: ((NEXT_DAY_AT.0 - days_ago * 86400) * 1000).into(),
            ..Default::default()
        }
    }

    #[test]
    fn delta_t_is_correct() -> Result<()> {
        assert_eq!(
            single_card_revlog_to_items(
                vec![
                    revlog(RevlogReviewKind::Learning, 1),
                    revlog(RevlogReviewKind::Review, 0)
                ],
                NEXT_DAY_AT
            ),
            Some(vec![FSRSItem {
                reviews: vec![
                    FSRSReview {
                        rating: 0,
                        delta_t: 0
                    },
                    FSRSReview {
                        rating: 0,
                        delta_t: 1
                    }
                ]
            }])
        );
        assert_eq!(
            single_card_revlog_to_items(
                vec![
                    revlog(RevlogReviewKind::Learning, 15),
                    revlog(RevlogReviewKind::Learning, 13),
                    revlog(RevlogReviewKind::Review, 10),
                    revlog(RevlogReviewKind::Review, 5)
                ],
                NEXT_DAY_AT,
            ),
            Some(vec![
                FSRSItem {
                    reviews: vec![
                        FSRSReview {
                            rating: 0,
                            delta_t: 0
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 2
                        }
                    ]
                },
                FSRSItem {
                    reviews: vec![
                        FSRSReview {
                            rating: 0,
                            delta_t: 0
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 2
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 3
                        }
                    ]
                },
                FSRSItem {
                    reviews: vec![
                        FSRSReview {
                            rating: 0,
                            delta_t: 0
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 2
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 3
                        },
                        FSRSReview {
                            rating: 0,
                            delta_t: 5
                        }
                    ]
                }
            ])
        );
        Ok(())
    }
}
