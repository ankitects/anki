// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::hours::Hour;
use anki_proto::stats::graphs_response::Hours;

use crate::revlog::RevlogReviewKind;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    pub(super) fn hours(&self) -> Hours {
        let mut data = Hours {
            one_month: vec![Default::default(); 24],
            three_months: vec![Default::default(); 24],
            one_year: vec![Default::default(); 24],
            all_time: vec![Default::default(); 24],
        };
        let mut conditional_buckets = [
            (
                self.next_day_start.adding_secs(-86_400 * 365),
                &mut data.one_year,
            ),
            (
                self.next_day_start.adding_secs(-86_400 * 90),
                &mut data.three_months,
            ),
            (
                self.next_day_start.adding_secs(-86_400 * 30),
                &mut data.one_month,
            ),
        ];
        'outer: for review in &self.revlog {
            if matches!(
                review.review_kind,
                RevlogReviewKind::Filtered
                    | RevlogReviewKind::Manual
                    | RevlogReviewKind::Rescheduled
            ) {
                continue;
            }
            let review_secs = review.id.as_secs();
            let hour = (((review_secs.0 + self.local_offset_secs) / 3600) % 24) as usize;
            let correct = review.button_chosen > 1;
            increment_count_for_hour(&mut data.all_time[hour], correct);
            for (stamp, bucket) in &mut conditional_buckets {
                if &review_secs < stamp {
                    continue 'outer;
                }
                increment_count_for_hour(&mut bucket[hour], correct);
            }
        }
        data
    }
}

pub(crate) fn increment_count_for_hour(hour: &mut Hour, correct: bool) {
    hour.total += 1;
    if correct {
        hour.correct += 1;
    }
}
