// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::ReviewCountsAndTimes;

use super::GraphsContext;
use crate::revlog::RevlogReviewKind;

impl GraphsContext {
    pub(super) fn review_counts_and_times(&self) -> ReviewCountsAndTimes {
        let mut data = ReviewCountsAndTimes::default();
        for review in &self.revlog {
            if review.review_kind == RevlogReviewKind::Manual
                || review.review_kind == RevlogReviewKind::Rescheduled
            {
                continue;
            }
            let day = (review.id.as_secs().elapsed_secs_since(self.next_day_start) / 86_400) as i32;
            let count = data.count.entry(day).or_insert_with(Default::default);
            let time = data.time.entry(day).or_insert_with(Default::default);
            match review.review_kind {
                RevlogReviewKind::Learning => {
                    count.learn += 1;
                    time.learn += review.taken_millis;
                }
                RevlogReviewKind::Relearning => {
                    count.relearn += 1;
                    time.relearn += review.taken_millis;
                }
                RevlogReviewKind::Review => {
                    if review.last_interval < 21 {
                        count.young += 1;
                        time.young += review.taken_millis;
                    } else {
                        count.mature += 1;
                        time.mature += review.taken_millis;
                    }
                }
                RevlogReviewKind::Filtered => {
                    count.filtered += 1;
                    time.filtered += review.taken_millis;
                }
                RevlogReviewKind::Manual | RevlogReviewKind::Rescheduled => unreachable!(),
            }
        }
        data
    }
}
