// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::GraphsContext;
use crate::pb::stats::graphs_response::buttons::ButtonCounts;
use crate::pb::stats::graphs_response::Buttons;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;

impl GraphsContext {
    pub(super) fn buttons(&self) -> Buttons {
        let mut all_time = ButtonCounts {
            learning: vec![0; 4],
            young: vec![0; 4],
            mature: vec![0; 4],
        };
        let mut conditional_buckets = vec![
            (
                self.next_day_start.adding_secs(-86_400 * 365),
                all_time.clone(),
            ),
            (
                self.next_day_start.adding_secs(-86_400 * 90),
                all_time.clone(),
            ),
            (
                self.next_day_start.adding_secs(-86_400 * 30),
                all_time.clone(),
            ),
        ];
        'outer: for review in &self.revlog {
            let Some(interval_bucket) = interval_bucket(review) else { continue; };
            let Some(button_idx) = button_index(review.button_chosen) else { continue; };
            let review_secs = review.id.as_secs();
            all_time.increment(interval_bucket, button_idx);
            for (stamp, bucket) in &mut conditional_buckets {
                if &review_secs < stamp {
                    continue 'outer;
                }
                bucket.increment(interval_bucket, button_idx);
            }
        }
        Buttons {
            one_month: Some(conditional_buckets.pop().unwrap().1),
            three_months: Some(conditional_buckets.pop().unwrap().1),
            one_year: Some(conditional_buckets.pop().unwrap().1),
            all_time: Some(all_time),
        }
    }
}

#[derive(Clone, Copy)]
enum IntervalBucket {
    Learning,
    Young,
    Mature,
}

impl ButtonCounts {
    fn increment(&mut self, bucket: IntervalBucket, button_idx: usize) {
        match bucket {
            IntervalBucket::Learning => self.learning[button_idx] += 1,
            IntervalBucket::Young => self.young[button_idx] += 1,
            IntervalBucket::Mature => self.mature[button_idx] += 1,
        }
    }
}

fn interval_bucket(review: &RevlogEntry) -> Option<IntervalBucket> {
    match review.review_kind {
        RevlogReviewKind::Learning | RevlogReviewKind::Relearning | RevlogReviewKind::Filtered => {
            Some(IntervalBucket::Learning)
        }
        RevlogReviewKind::Review => Some(if review.last_interval < 21 {
            IntervalBucket::Young
        } else {
            IntervalBucket::Mature
        }),
        RevlogReviewKind::Manual => None,
    }
}

fn button_index(button_chosen: u8) -> Option<usize> {
    if (1..=4).contains(&button_chosen) {
        Some((button_chosen - 1) as usize)
    } else {
        None
    }
}
