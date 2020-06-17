// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    card::{CardQueue, CardType},
    config::SchedulerVersion,
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
    sched::cutoff::SchedTimingToday,
    search::SortMode,
};

struct GraphsContext {
    scheduler: SchedulerVersion,
    timing: SchedTimingToday,
    /// Based on the set rollover hour.
    today_rolled_over_at_millis: i64,
    /// Seconds to add to UTC timestamps to get local time.
    local_offset_secs: i64,
    stats: AllStats,
}

#[derive(Debug)]
struct AllStats {
    today: pb::TodayGraphData,
    buttons: pb::ButtonsGraphData,
    hours: Vec<pb::HourGraphData>,
    cards: pb::CardsGraphData,
}

impl Default for AllStats {
    fn default() -> Self {
        let buttons = pb::ButtonsGraphData {
            learn: vec![0; 4],
            young: vec![0; 4],
            mature: vec![0; 4],
        };
        AllStats {
            today: Default::default(),
            buttons,
            hours: vec![Default::default(); 24],
            cards: Default::default(),
        }
    }
}

impl From<AllStats> for pb::GraphsOut {
    fn from(s: AllStats) -> Self {
        pb::GraphsOut {
            cards: Some(s.cards),
            hours: s.hours,
            today: Some(s.today),
            buttons: Some(s.buttons),
        }
    }
}

#[derive(Default, Debug)]
struct ButtonStats {
    /// In V1 scheduler, 4th element is ignored
    learn: [u32; 4],
    young: [u32; 4],
    mature: [u32; 4],
}

#[derive(Default, Debug)]
struct HourStats {
    review_count: u32,
    correct_count: u32,
}

#[derive(Default, Debug)]
struct CardStats {
    card_count: u32,
    note_count: u32,
    ease_factor_min: f32,
    ease_factor_max: f32,
    ease_factor_sum: f32,
    ease_factor_count: u32,
    mature_count: u32,
    young_or_learning_count: u32,
    new_count: u32,
    suspended_or_buried_count: u32,
}

impl GraphsContext {
    fn observe_card(&mut self, card: &Card) {
        self.observe_card_stats_for_card(card);
    }

    fn observe_review(&mut self, entry: &RevlogEntry) {
        self.observe_button_stats_for_review(entry);
        self.observe_hour_stats_for_review(entry);
        self.observe_today_stats_for_review(entry);
    }

    fn observe_button_stats_for_review(&mut self, review: &RevlogEntry) {
        let mut button_num = review.button_chosen as usize;
        if button_num == 0 {
            return;
        }

        let buttons = &mut self.stats.buttons;
        let category = match review.review_kind {
            RevlogReviewKind::Learning | RevlogReviewKind::Relearning => {
                // V1 scheduler only had 3 buttons in learning
                if button_num == 4 && self.scheduler == SchedulerVersion::V1 {
                    button_num = 3;
                }

                &mut buttons.learn
            }
            RevlogReviewKind::Review | RevlogReviewKind::EarlyReview => {
                if review.last_interval < 21 {
                    &mut buttons.young
                } else {
                    &mut buttons.mature
                }
            }
        };

        if let Some(count) = category.get_mut(button_num - 1) {
            *count += 1;
        }
    }

    fn observe_hour_stats_for_review(&mut self, review: &RevlogEntry) {
        match review.review_kind {
            RevlogReviewKind::Learning
            | RevlogReviewKind::Review
            | RevlogReviewKind::Relearning => {
                let hour_idx = (((review.id.0 / 1000) + self.local_offset_secs) / 3600) % 24;
                let hour = &mut self.stats.hours[hour_idx as usize];

                hour.review_count += 1;
                if review.button_chosen != 1 {
                    hour.correct_count += 1;
                }
            }
            RevlogReviewKind::EarlyReview => {}
        }
    }

    fn observe_today_stats_for_review(&mut self, review: &RevlogEntry) {
        if review.id.0 < self.today_rolled_over_at_millis {
            return;
        }

        let today = &mut self.stats.today;

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
            RevlogReviewKind::EarlyReview => today.early_review_count += 1,
        }
    }

    fn observe_card_stats_for_card(&mut self, card: &Card) {
        let cstats = &mut self.stats.cards;

        cstats.card_count += 1;

        // counts by type
        match card.queue {
            CardQueue::New => cstats.new_count += 1,
            CardQueue::Review if card.ivl >= 21 => cstats.mature_count += 1,
            CardQueue::Review | CardQueue::Learn | CardQueue::DayLearn => {
                cstats.young_or_learning_count += 1
            }
            CardQueue::Suspended | CardQueue::UserBuried | CardQueue::SchedBuried => {
                cstats.suspended_or_buried_count += 1
            }
            CardQueue::PreviewRepeat => {}
        }

        // ease factor
        if card.ctype == CardType::Review {
            let ease_factor = (card.factor as f32) / 1000.0;

            cstats.ease_factor_count += 1;
            cstats.ease_factor_sum += ease_factor;

            if ease_factor < cstats.ease_factor_min || cstats.ease_factor_min == 0.0 {
                cstats.ease_factor_min = ease_factor;
            }

            if ease_factor > cstats.ease_factor_max {
                cstats.ease_factor_max = ease_factor;
            }
        }
    }
}

impl Collection {
    pub(crate) fn graph_data_for_search(
        &mut self,
        search: &str,
        days: u32,
    ) -> Result<pb::GraphsOut> {
        let cids = self.search_cards(search, SortMode::NoOrder)?;
        let stats = self.graph_data(&cids, days)?;
        println!("{:#?}", stats);
        Ok(stats.into())
    }

    fn graph_data(&self, cids: &[CardID], days: u32) -> Result<AllStats> {
        let timing = self.timing_today()?;
        let revlog_start = TimestampSecs(if days > 0 {
            timing.next_day_at - (((days as i64) + 1) * 86_400)
        } else {
            0
        });

        let offset = self.local_offset();
        let local_offset_secs = offset.local_minus_utc() as i64;

        let mut ctx = GraphsContext {
            scheduler: self.sched_ver(),
            today_rolled_over_at_millis: (timing.next_day_at - 86_400) * 1000,
            timing,
            local_offset_secs,
            stats: AllStats::default(),
        };

        for cid in cids {
            let card = self.storage.get_card(*cid)?.ok_or(AnkiError::NotFound)?;
            ctx.observe_card(&card);
            self.storage
                .for_each_revlog_entry_of_card(*cid, revlog_start, |entry| {
                    Ok(ctx.observe_review(entry))
                })?;
        }

        ctx.stats.cards.note_count = self.storage.note_ids_of_cards(cids)?.len() as u32;

        Ok(ctx.stats)
    }
}
