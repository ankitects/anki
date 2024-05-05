// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::ComputeOptimalRetentionRequest;
use anki_proto::scheduler::OptimalRetentionParameters;
use fsrs::SimulatorConfig;
use fsrs::FSRS;
use itertools::Itertools;

use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::search::SortMode;

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeRetentionProgress {
    pub current: u32,
    pub total: u32,
}

impl Collection {
    pub fn compute_optimal_retention(
        &mut self,
        req: ComputeOptimalRetentionRequest,
    ) -> Result<f32> {
        let mut anki_progress = self.new_progress_handler::<ComputeRetentionProgress>();
        let fsrs = FSRS::new(None)?;
        if req.days_to_simulate == 0 {
            invalid_input!("no days to simulate")
        }
        let revlogs = self
            .search_cards_into_table(&req.search, SortMode::NoOrder)?
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let p = self.get_optimal_retention_parameters(revlogs)?;
        let learn_span = req.days_to_simulate as usize;
        let learn_limit = 10;
        let deck_size = learn_span * learn_limit;
        Ok(fsrs
            .optimal_retention(
                &SimulatorConfig {
                    deck_size,
                    learn_span: req.days_to_simulate as usize,
                    max_cost_perday: f64::MAX,
                    max_ivl: req.max_interval as f64,
                    recall_costs: [p.recall_secs_hard, p.recall_secs_good, p.recall_secs_easy],
                    forget_cost: p.forget_secs,
                    learn_cost: p.learn_secs,
                    first_rating_prob: [
                        p.first_rating_probability_again,
                        p.first_rating_probability_hard,
                        p.first_rating_probability_good,
                        p.first_rating_probability_easy,
                    ],
                    review_rating_prob: [
                        p.review_rating_probability_hard,
                        p.review_rating_probability_good,
                        p.review_rating_probability_easy,
                    ],
                    loss_aversion: req.loss_aversion,
                    learn_limit,
                    review_limit: usize::MAX,
                },
                &req.weights,
                |ip| {
                    anki_progress
                        .update(false, |p| {
                            p.current = ip.current as u32;
                        })
                        .is_ok()
                },
            )?
            .max(0.75)
            .min(0.95) as f32)
    }

    pub fn get_optimal_retention_parameters(
        &mut self,
        revlogs: Vec<RevlogEntry>,
    ) -> Result<OptimalRetentionParameters> {
        if revlogs.len() < 400 {
            return Err(AnkiError::FsrsInsufficientReviews {
                count: revlogs.len(),
            });
        }
        let first_rating_count = revlogs
            .iter()
            .group_by(|r| r.cid)
            .into_iter()
            .map(|(_cid, group)| {
                group
                    .into_iter()
                    .find(|r| r.review_kind == RevlogReviewKind::Learning && r.button_chosen >= 1)
            })
            .filter(|r| r.is_some())
            .counts_by(|r| r.unwrap().button_chosen);
        let total_first = first_rating_count.values().sum::<usize>() as f64;
        let first_rating_prob = if total_first > 0.0 {
            let mut arr = [0.0; 4];
            first_rating_count
                .iter()
                .for_each(|(button_chosen, count)| {
                    arr[*button_chosen as usize - 1] = *count as f64 / total_first
                });
            arr
        } else {
            return Err(AnkiError::FsrsInsufficientData);
        };

        let review_rating_count = revlogs
            .iter()
            .filter(|r| r.review_kind == RevlogReviewKind::Review && r.button_chosen != 1)
            .counts_by(|r| r.button_chosen);
        let total_reviews = review_rating_count.values().sum::<usize>();
        let review_rating_prob = if total_reviews as f64 > 0.0 {
            let mut arr = [0.0; 3];
            review_rating_count
                .iter()
                .filter(|(&button_chosen, ..)| button_chosen >= 2)
                .for_each(|(button_chosen, count)| {
                    arr[*button_chosen as usize - 2] = *count as f64 / total_reviews as f64;
                });
            arr
        } else {
            return Err(AnkiError::FsrsInsufficientData);
        };

        let recall_costs = {
            let default = [14.0, 14.0, 10.0, 6.0];
            let mut arr = default;
            revlogs
                .iter()
                .filter(|r| {
                    r.review_kind == RevlogReviewKind::Review
                        && r.button_chosen > 0
                        && r.taken_millis > 0
                        && r.taken_millis < 1200000 // 20 minutes
                })
                .sorted_by(|a, b| a.button_chosen.cmp(&b.button_chosen))
                .group_by(|r| r.button_chosen)
                .into_iter()
                .for_each(|(button_chosen, group)| {
                    let group_vec = group.into_iter().map(|r| r.taken_millis).collect_vec();
                    arr[button_chosen as usize - 1] = median_secs(&group_vec);
                });
            if arr == default {
                return Err(AnkiError::FsrsInsufficientData);
            }
            arr
        };
        let learn_cost = {
            let revlogs_filter = revlogs
                .iter()
                .filter(|r| {
                    r.review_kind == RevlogReviewKind::Learning
                        && r.button_chosen >= 1
                        && r.taken_millis > 0
                        && r.taken_millis < 1200000 // 20 minutes
                })
                .map(|r| r.taken_millis);
            let group_vec = revlogs_filter.collect_vec();
            median_secs(&group_vec)
        };
        if learn_cost == 0.0 {
            return Err(AnkiError::FsrsInsufficientData);
        }

        let forget_cost = {
            let review_kind_to_total_millis = revlogs
                .iter()
                .filter(|r| {
                    r.button_chosen > 0 && r.taken_millis > 0 && r.taken_millis < 1200000
                    // 20 minutes
                })
                .sorted_by(|a, b| a.cid.cmp(&b.cid).then(a.id.cmp(&b.id)))
                .group_by(|r| r.review_kind)
                /*
                    for example:
                    o  x x  o o x x x o o x x o x
                      |<->|    |<--->|   |<->| |<>|
                    x means forgotten, there are 4 consecutive sets of internal relearning in this card.
                    So each group is counted separately, and each group is summed up internally.(following code)
                    Finally averaging all groups, so sort by cid and id.
                */
                .into_iter()
                .map(|(review_kind, group)| {
                    let total_millis: u32 = group.into_iter().map(|r| r.taken_millis).sum();
                    (review_kind, total_millis)
                })
                .collect_vec();
            let mut group_sec_by_review_kind: [Vec<_>; 5] = Default::default();
            for (review_kind, sec) in review_kind_to_total_millis.into_iter() {
                group_sec_by_review_kind[review_kind as usize].push(sec)
            }
            let mut arr = [0.0; 5];
            for (review_kind, group) in group_sec_by_review_kind.iter().enumerate() {
                arr[review_kind] = median_secs(group);
            }
            arr
        };

        let forget_cost = forget_cost[RevlogReviewKind::Relearning as usize] + recall_costs[0];

        let params = OptimalRetentionParameters {
            recall_secs_hard: recall_costs[1],
            recall_secs_good: recall_costs[2],
            recall_secs_easy: recall_costs[3],
            forget_secs: forget_cost,
            learn_secs: learn_cost,
            first_rating_probability_again: first_rating_prob[0],
            first_rating_probability_hard: first_rating_prob[1],
            first_rating_probability_good: first_rating_prob[2],
            first_rating_probability_easy: first_rating_prob[3],
            review_rating_probability_hard: review_rating_prob[0],
            review_rating_probability_good: review_rating_prob[1],
            review_rating_probability_easy: review_rating_prob[2],
        };
        Ok(params)
    }
}

fn median_secs(group: &Vec<u32>) -> f64 {
    let length = group.len();
    if length > 0 {
        let mut group_vec = group.clone();
        group_vec.sort_unstable();
        let median_millis = if length % 2 == 0 {
            let mid = length / 2;
            (group_vec[mid - 1] + group_vec[mid]) as f64 / 2.0
        } else {
            group_vec[length / 2] as f64
        };
        median_millis / 1000.0
    } else {
        0.0
    }
}
