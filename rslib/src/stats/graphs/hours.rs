// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::stats::graphs_response::hours::Hour;
use anki_proto::stats::graphs_response::DayHours;
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

    /// Review counts for each local hour of each Anki day. The day key matches
    /// `review_counts_and_times`: 0 is today, negative is the past. Filtered
    /// reviews count, so a day's hours add up to that day's review total.
    pub(super) fn hours_by_day(&self) -> HashMap<i32, DayHours> {
        let mut totals: HashMap<i32, [u32; 24]> = HashMap::new();
        for review in &self.revlog {
            if review.review_kind == RevlogReviewKind::Manual
                || review.review_kind == RevlogReviewKind::Rescheduled
            {
                continue;
            }
            let day = (review.id.as_secs().elapsed_secs_since(self.next_day_start) / 86_400) as i32;
            let secs = review.id.as_secs().0 + self.local_offset_secs;
            let hour = secs.div_euclid(3600).rem_euclid(24) as usize;
            totals.entry(day).or_insert([0; 24])[hour] += 1;
        }
        totals
            .into_iter()
            .map(|(day, hours)| {
                (
                    day,
                    DayHours {
                        total: hours.to_vec(),
                    },
                )
            })
            .collect()
    }
}

pub(crate) fn increment_count_for_hour(hour: &mut Hour, correct: bool) {
    hour.total += 1;
    if correct {
        hour.correct += 1;
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::prelude::TimestampSecs;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogId;

    #[test]
    fn hours_by_day_uses_local_hour_and_review_day_and_skips_manual_entries() {
        let next_day_start = TimestampSecs(100 * 86_400);
        let revlog = vec![
            review(next_day_start.0 - 3_600, RevlogReviewKind::Learning),
            review(next_day_start.0 - 7_200, RevlogReviewKind::Filtered),
            review(next_day_start.0 - 86_400 - 3_600, RevlogReviewKind::Review),
            review(next_day_start.0 - 10_800, RevlogReviewKind::Manual),
            review(next_day_start.0 - 14_400, RevlogReviewKind::Rescheduled),
        ];
        let context = GraphsContext {
            revlog,
            cards: vec![],
            next_day_start,
            days_elapsed: 1,
            local_offset_secs: 3_600,
        };

        let hours = context.hours_by_day();
        assert_eq!(hours.len(), 2);

        let today = &hours[&0].total;
        assert_eq!(today.len(), 24);
        assert_eq!(today[0], 1);
        assert_eq!(today[23], 1);
        assert_eq!(today.iter().sum::<u32>(), 2);

        let yesterday = &hours[&-1].total;
        assert_eq!(yesterday.len(), 24);
        assert_eq!(yesterday[0], 1);
        assert_eq!(yesterday.iter().sum::<u32>(), 1);
    }

    fn review(timestamp_secs: i64, review_kind: RevlogReviewKind) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(timestamp_secs * 1_000),
            review_kind,
            ..Default::default()
        }
    }
}
