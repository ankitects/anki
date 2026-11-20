// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use anki_proto::stats::graphs_response::true_retention_stats::TrueRetention;
use anki_proto::stats::graphs_response::TrueRetentionStats;

use super::GraphsContext;
use super::TimestampSecs;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;

impl GraphsContext {
    pub fn calculate_true_retention(&self) -> TrueRetentionStats {
        let mut stats = TrueRetentionStats::default();

        // create periods
        let day = 86400;
        let periods = vec![
            (
                "today",
                self.next_day_start.adding_secs(-day),
                self.next_day_start,
            ),
            (
                "yesterday",
                self.next_day_start.adding_secs(-2 * day),
                self.next_day_start.adding_secs(-day),
            ),
            (
                "week",
                self.next_day_start.adding_secs(-7 * day),
                self.next_day_start,
            ),
            (
                "month",
                self.next_day_start.adding_secs(-30 * day),
                self.next_day_start,
            ),
            (
                "year",
                self.next_day_start.adding_secs(-365 * day),
                self.next_day_start,
            ),
            ("all_time", TimestampSecs(0), self.next_day_start),
        ];

        // create period stats
        let mut period_stats: HashMap<&str, TrueRetention> = periods
            .iter()
            .map(|(name, _, _)| (*name, TrueRetention::default()))
            .collect();

        for review in first_retention_reviews_by_card_day(&self.revlog, self.next_day_start) {
            for (period_name, start, end) in &periods {
                if review.id.as_secs() >= *start && review.id.as_secs() < *end {
                    let period_stat = period_stats.get_mut(period_name).unwrap();
                    const MATURE_IVL: i32 = 21; // mature interval is 21 days
                    match (review.last_interval < MATURE_IVL, review.button_chosen) {
                        (true, 1) => period_stat.young_failed += 1,
                        (true, _) => period_stat.young_passed += 1,
                        (false, 1) => period_stat.mature_failed += 1,
                        (false, _) => period_stat.mature_passed += 1,
                    }
                }
            }
        }

        stats.today = Some(period_stats["today"]);
        stats.yesterday = Some(period_stats["yesterday"]);
        stats.week = Some(period_stats["week"]);
        stats.month = Some(period_stats["month"]);
        stats.year = Some(period_stats["year"]);
        stats.all_time = Some(period_stats["all_time"]);

        stats
    }
}

fn first_retention_reviews_by_card_day(
    revlog: &[RevlogEntry],
    next_day_start: TimestampSecs,
) -> Vec<&RevlogEntry> {
    let mut reviews_by_card_day = HashMap::new();
    for review in revlog.iter().filter(|review| is_retention_review(review)) {
        let day = retention_day(review.id.as_secs(), next_day_start);
        reviews_by_card_day
            .entry((review.cid, day))
            .and_modify(|first: &mut &RevlogEntry| {
                if review.id < first.id {
                    *first = review;
                }
            })
            .or_insert(review);
    }
    reviews_by_card_day.into_values().collect()
}

fn is_retention_review(review: &RevlogEntry) -> bool {
    review.has_rating_and_affects_scheduling()
        // cards with an interval ≥ 1 day
        && (review.review_kind == RevlogReviewKind::Review
            || review.last_interval <= -86400
            || review.last_interval >= 1)
}

fn retention_day(review_secs: TimestampSecs, next_day_start: TimestampSecs) -> i64 {
    review_secs.elapsed_secs_since(next_day_start) / 86_400
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;

    const DAY: i64 = 86_400;
    const NEXT_DAY_START: TimestampSecs = TimestampSecs(10 * DAY);

    #[test]
    fn true_retention_counts_first_review_per_card_day() {
        let ctx = context_with_revlog(vec![
            review(1, DAY - 60, 3),
            review(1, DAY - 30, 1),
            review(2, DAY - 30, 3),
        ]);

        let stats = ctx.calculate_true_retention();
        let today = stats.today.unwrap();
        assert_eq!(today.young_failed, 1);
        assert_eq!(today.young_passed, 1);
    }

    #[test]
    fn true_retention_counts_the_same_card_on_different_days() {
        let ctx = context_with_revlog(vec![review(1, DAY + 60, 1), review(1, DAY - 60, 3)]);

        let stats = ctx.calculate_true_retention();
        assert_eq!(stats.today.unwrap().young_passed, 1);
        let week = stats.week.unwrap();
        assert_eq!(week.young_failed, 1);
        assert_eq!(week.young_passed, 1);
    }

    fn context_with_revlog(revlog: Vec<RevlogEntry>) -> GraphsContext {
        GraphsContext {
            revlog,
            cards: vec![],
            fsrs_by_preset: Default::default(),
            fsrs_preset_by_card: Default::default(),
            rwkv_retrievability_scores: None,
            next_day_start: NEXT_DAY_START,
            days_elapsed: 10,
            local_offset_secs: 0,
        }
    }

    fn review(cid: i64, seconds_before_next_day: i64, button_chosen: u8) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId((NEXT_DAY_START.0 - seconds_before_next_day) * 1000),
            cid: CardId(cid),
            usn: Usn(0),
            button_chosen,
            interval: 10,
            last_interval: 10,
            review_kind: RevlogReviewKind::Review,
            ..Default::default()
        }
    }
}
