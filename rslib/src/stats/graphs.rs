// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, prelude::*, revlog::RevlogEntry};

// impl GraphsContext {
//     fn observe_button_stats_for_review(&mut self, review: &RevlogEntry) {
//         let mut button_num = review.button_chosen as usize;
//         if button_num == 0 {
//             return;
//         }

//         let buttons = &mut self.stats.buttons;
//         let category = match review.review_kind {
//             RevlogReviewKind::Learning | RevlogReviewKind::Relearning => {
//                 // V1 scheduler only had 3 buttons in learning
//                 if button_num == 4 && self.scheduler == SchedulerVersion::V1 {
//                     button_num = 3;
//                 }

//                 &mut buttons.learn
//             }
//             RevlogReviewKind::Review | RevlogReviewKind::EarlyReview => {
//                 if review.last_interval < 21 {
//                     &mut buttons.young
//                 } else {
//                     &mut buttons.mature
//                 }
//             }
//         };

//         if let Some(count) = category.get_mut(button_num - 1) {
//             *count += 1;
//         }
//     }

//     fn observe_hour_stats_for_review(&mut self, review: &RevlogEntry) {
//         match review.review_kind {
//             RevlogReviewKind::Learning
//             | RevlogReviewKind::Review
//             | RevlogReviewKind::Relearning => {
//                 let hour_idx = (((review.id.0 / 1000) + self.local_offset_secs) / 3600) % 24;
//                 let hour = &mut self.stats.hours[hour_idx as usize];

//                 hour.review_count += 1;
//                 if review.button_chosen != 1 {
//                     hour.correct_count += 1;
//                 }
//             }
//             RevlogReviewKind::EarlyReview => {}
//         }
//     }

//     fn observe_today_stats_for_review(&mut self, review: &RevlogEntry) {
//         if review.id.0 < self.today_rolled_over_at_millis {
//             return;
//         }

//         let today = &mut self.stats.today;

//         // total
//         today.answer_count += 1;
//         today.answer_millis += review.taken_millis;

//         // correct
//         if review.button_chosen > 1 {
//             today.correct_count += 1;
//         }

//         // mature
//         if review.last_interval >= 21 {
//             today.mature_count += 1;
//             if review.button_chosen > 1 {
//                 today.mature_correct += 1;
//             }
//         }

//         // type counts
//         match review.review_kind {
//             RevlogReviewKind::Learning => today.learn_count += 1,
//             RevlogReviewKind::Review => today.review_count += 1,
//             RevlogReviewKind::Relearning => today.relearn_count += 1,
//             RevlogReviewKind::EarlyReview => today.early_review_count += 1,
//         }
//     }

//     fn observe_card_stats_for_card(&mut self, card: &Card) {
//         counts by type
//         match card.queue {
//             CardQueue::New => cstats.new_count += 1,
//             CardQueue::Review if card.ivl >= 21 => cstats.mature_count += 1,
//             CardQueue::Review | CardQueue::Learn | CardQueue::DayLearn => {
//                 cstats.young_or_learning_count += 1
//             }
//             CardQueue::Suspended | CardQueue::UserBuried | CardQueue::SchedBuried => {
//                 cstats.suspended_or_buried_count += 1
//             }
//             CardQueue::PreviewRepeat => {}
//         }
//     }
// }

impl Collection {
    pub(crate) fn graph_data_for_search(
        &mut self,
        search: &str,
        days: u32,
    ) -> Result<pb::GraphsOut> {
        self.search_cards_into_table(search)?;
        let all = search.trim().is_empty();
        self.graph_data(all, days)
    }

    fn graph_data(&self, all: bool, days: u32) -> Result<pb::GraphsOut> {
        let timing = self.timing_today()?;
        let revlog_start = TimestampSecs(if days > 0 {
            timing.next_day_at - (((days as i64) + 1) * 86_400)
        } else {
            0
        });

        let offset = self.local_offset();
        let local_offset_secs = offset.local_minus_utc() as i64;

        let cards = self.storage.all_searched_cards()?;
        let revlog = if all {
            self.storage.get_all_revlog_entries(revlog_start)?
        } else {
            self.storage
                .get_revlog_entries_for_searched_cards(revlog_start)?
        };

        self.clear_searched_cards()?;

        Ok(pb::GraphsOut {
            cards: cards.into_iter().map(Into::into).collect(),
            revlog,
            days_elapsed: timing.days_elapsed,
            next_day_at_secs: timing.next_day_at as u32,
            scheduler_version: self.sched_ver() as u32,
            local_offset_secs: local_offset_secs as u32,
            note_count: 0,
        })
    }
}

impl From<RevlogEntry> for pb::RevlogEntry {
    fn from(e: RevlogEntry) -> Self {
        pb::RevlogEntry {
            id: e.id.0,
            cid: e.cid.0,
            usn: e.usn.0,
            button_chosen: e.button_chosen as u32,
            interval: e.interval,
            last_interval: e.last_interval,
            ease_factor: e.ease_factor,
            taken_millis: e.taken_millis,
            review_kind: e.review_kind as u32,
        }
    }
}
