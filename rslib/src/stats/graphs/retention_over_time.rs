// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::RetentionOverTime;

use super::retention::counts_toward_true_retention;
use super::GraphsContext;

impl GraphsContext {
    /// Daily review and pass counts behind the rolling retention graph.
    pub(super) fn retention_over_time(&self) -> RetentionOverTime {
        let mut data = RetentionOverTime::default();
        for review in self
            .revlog
            .iter()
            .filter(|review| counts_toward_true_retention(review))
        {
            let day = (review.id.as_secs().elapsed_secs_since(self.next_day_start) / 86_400) as i32;
            let entry = data.days.entry(day).or_default();
            entry.reviews += 1;
            if review.button_chosen > 1 {
                entry.passed += 1;
            }
        }
        data
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;

    fn review(id: i64, button: u8, kind: RevlogReviewKind) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(id),
            button_chosen: button,
            last_interval: 5,
            ease_factor: 2500,
            review_kind: kind,
            ..Default::default()
        }
    }

    #[test]
    fn daily_totals_count_rated_reviews() {
        let next_day_start = TimestampSecs(10 * 86_400);
        let today = 9 * 86_400_000 + 1000;
        let yesterday = today - 86_400_000;
        let ctx = GraphsContext {
            revlog: vec![
                review(yesterday, 3, RevlogReviewKind::Review),
                review(today, 1, RevlogReviewKind::Review),
                review(today + 1, 3, RevlogReviewKind::Review),
                // not counted: no rating
                review(today + 2, 0, RevlogReviewKind::Manual),
            ],
            cards: vec![],
            next_day_start,
            days_elapsed: 9,
            local_offset_secs: 0,
        };
        let data = ctx.retention_over_time();
        let day = data.days[&0];
        assert_eq!((day.reviews, day.passed), (2, 1));
        let day = data.days[&-1];
        assert_eq!((day.reviews, day.passed), (1, 1));
    }
}
