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

        let mut period_learned_cards: HashMap<&str, HashMap<i64, bool>> = periods
            .iter()
            .map(|(name, _, _)| (*name, HashMap::new()))
            .collect();
        for review in &self.revlog {
            for (period_name, start, end) in &periods {
                if review.id.as_secs() >= *start && review.id.as_secs() < *end {
                    let period_stat = period_stats.get_mut(period_name).unwrap();
                    let learned_cards = period_learned_cards.get_mut(period_name).unwrap();
                    const MATURE_IVL: i32 = 21; // mature interval is 21 days

                    match review.review_kind {
                        RevlogReviewKind::Learning
                        | RevlogReviewKind::Review
                        | RevlogReviewKind::Relearning => {
                            if review.last_interval < MATURE_IVL
                                && review.button_chosen == 1
                                && (review.review_kind == RevlogReviewKind::Review
                                    || review.last_interval <= -86400
                                    || review.last_interval >= 1)
                            {
                                period_stat.young_failed += 1;
                            } else if review.last_interval < MATURE_IVL
                                && review.button_chosen > 1
                                && (review.review_kind == RevlogReviewKind::Review
                                    || review.last_interval <= -86400
                                    || review.last_interval >= 1)
                            {
                                period_stat.young_passed += 1;
                            } else if review.last_interval >= MATURE_IVL
                                && review.button_chosen == 1
                                && (review.review_kind == RevlogReviewKind::Review
                                    || review.last_interval <= -86400
                                    || review.last_interval >= 1)
                            {
                                period_stat.mature_failed += 1;
                            } else if review.last_interval >= MATURE_IVL
                                && review.button_chosen > 1
                                && (review.review_kind == RevlogReviewKind::Review
                                    || review.last_interval <= -86400
                                    || review.last_interval >= 1)
                            {
                                period_stat.mature_passed += 1;
                            }

                            if review.review_kind == RevlogReviewKind::Learning
                                && (review.interval >= 1 || review.interval <= -86400)
                            {
                                learned_cards.insert(review.cid.into(), true);
                            }

                            if (review.review_kind == RevlogReviewKind::Relearning
                                && (review.interval >= 1 || review.interval <= -86400)
                                && review.last_interval > -86400
                                && review.last_interval <= 0)
                                || (review.review_kind == RevlogReviewKind::Learning
                                    && (review.last_interval <= -86400
                                        || review.last_interval >= 1)
                                    && review.button_chosen == 1)
                            {
                                period_stat.relearned += 1;
                            }
                        }
                        RevlogReviewKind::Filtered | RevlogReviewKind::Manual => {}
                    }
                }
            }
        }

        // set learned value for each period
        for (period_name, learned_cards) in &period_learned_cards {
            period_stats.get_mut(period_name).unwrap().learned = learned_cards.len() as u32;
        }

        stats.today = Some(period_stats["today"].clone());
        stats.yesterday = Some(period_stats["yesterday"].clone());
        stats.week = Some(period_stats["week"].clone());
        stats.month = Some(period_stats["month"].clone());
        stats.year = Some(period_stats["year"].clone());
        stats.all_time = Some(period_stats["all_time"].clone());

        stats
    }
}
