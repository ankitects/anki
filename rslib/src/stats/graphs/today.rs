// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::graphs_response::Today;

use crate::revlog::RevlogReviewKind;
use crate::stats::graphs::GraphsContext;

impl GraphsContext {
    pub(super) fn today(&self) -> Today {
        let mut today = Today::default();
        let start_of_today_ms = self.next_day_start.adding_secs(-86_400).as_millis().0;
        for review in self.revlog.iter().rev() {
            if review.id.0 < start_of_today_ms {
                continue;
            }
            if review.review_kind == RevlogReviewKind::Manual
                || review.review_kind == RevlogReviewKind::Rescheduled
            {
                continue;
            }
            // total
            today.answer_count += 1;
            today.answer_millis += review.taken_millis;
            // correct
            if review.button_chosen > 1 {
                today.correct_count += 1;
            }
            // mature
            if review.last_interval >= 21 {
                today.mature_count += 1;
                if review.button_chosen > 1 {
                    today.mature_correct += 1;
                }
            }
            // type counts
            match review.review_kind {
                RevlogReviewKind::Learning => today.learn_count += 1,
                RevlogReviewKind::Review => today.review_count += 1,
                RevlogReviewKind::Relearning => today.relearn_count += 1,
                RevlogReviewKind::Filtered => today.early_review_count += 1,
                RevlogReviewKind::Manual | RevlogReviewKind::Rescheduled => unreachable!(),
            }
        }
        today
    }
}
