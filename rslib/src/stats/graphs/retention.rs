// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use anki_proto::stats::graphs_response::true_retention_stats::TrueRetention;
use anki_proto::stats::graphs_response::TrueRetentionStats;

use super::GraphsContext;
use super::TimestampSecs;
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

        self.revlog
            .iter()
            .filter(|review| {
                review.has_rating_and_affect_scheduling()
                    // cards with an interval ≥ 1 day
                    && (review.review_kind == RevlogReviewKind::Review
                        || review.last_interval <= -86400
                        || review.last_interval >= 1)
            })
            .for_each(|review| {
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
            });

        stats.today = Some(period_stats["today"]);
        stats.yesterday = Some(period_stats["yesterday"]);
        stats.week = Some(period_stats["week"]);
        stats.month = Some(period_stats["month"]);
        stats.year = Some(period_stats["year"]);
        stats.all_time = Some(period_stats["all_time"]);

        stats
    }
}
