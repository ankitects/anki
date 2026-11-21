// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::RetentionOverTime;

use super::retention::counts_toward_true_retention;
use super::GraphsContext;
use crate::revlog::RevlogId;

impl GraphsContext {
    /// Daily actual and RWKV-predicted retention. `prediction` gives the
    /// recall predicted just before a review, when known.
    pub(super) fn retention_over_time(
        &self,
        prediction: impl Fn(RevlogId) -> Option<f32>,
    ) -> RetentionOverTime {
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
            if let Some(predicted) = prediction(review.id) {
                entry.predicted_reviews += 1;
                entry.predicted_sum += predicted;
            }
        }
        data
    }
}

#[cfg(test)]
mod test {
    use std::collections::HashMap;

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
    fn daily_totals_combine_actual_and_predicted() {
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
        let predictions = HashMap::from([(today, 0.5), (today + 1, 0.9)]);
        let data = ctx.retention_over_time(|id| predictions.get(&id.0).copied());
        let day = data.days[&0];
        assert_eq!((day.reviews, day.passed, day.predicted_reviews), (2, 1, 2));
        assert!((day.predicted_sum - 1.4).abs() < 1e-6);
        let day = data.days[&-1];
        assert_eq!((day.reviews, day.passed, day.predicted_reviews), (1, 1, 0));
    }
}
