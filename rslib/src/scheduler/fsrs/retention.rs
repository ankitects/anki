// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::sync::Arc;

use anki_proto::scheduler::ComputeOptimalRetentionRequest;
use fsrs::extract_simulator_config;
use fsrs::PostSchedulingFn;
use fsrs::SimulatorConfig;
use fsrs::FSRS;

use super::simulator::apply_load_balance_and_easy_days;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::states::load_balancer::parse_easy_days_percentages;
use crate::search::SortMode;

#[derive(Default, Clone, Copy, Debug)]
pub struct ComputeRetentionProgress {
    pub current: u32,
    pub total: u32,
}

impl Collection {
    pub fn compute_optimal_retention(
        &mut self,
        req: ComputeOptimalRetentionRequest,
    ) -> Result<f32> {
        let mut anki_progress = self.new_progress_handler::<ComputeRetentionProgress>();
        let fsrs = FSRS::new(None)?;
        if req.days_to_simulate == 0 {
            invalid_input!("no days to simulate")
        }
        let revlogs = self
            .search_cards_into_table(&req.search, SortMode::NoOrder)?
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let p = self.get_optimal_retention_parameters(revlogs)?;
        let learn_span = req.days_to_simulate as usize;
        let learn_limit = 10;
        let deck_size = learn_span * learn_limit;
        let easy_days_percentages = parse_easy_days_percentages(&req.easy_days_percentages)?;
        let next_day_at = self.timing_today()?.next_day_at;
        let post_scheduling_fn: Option<PostSchedulingFn> =
            if self.get_config_bool(BoolKey::LoadBalancerEnabled) {
                Some(PostSchedulingFn(Arc::new(
                    move |card, max_interval, today, due_cnt_per_day, rng| {
                        apply_load_balance_and_easy_days(
                            card.interval,
                            max_interval,
                            today,
                            due_cnt_per_day,
                            rng,
                            next_day_at,
                            &easy_days_percentages,
                        )
                    },
                )))
            } else {
                None
            };
        Ok(fsrs
            .optimal_retention(
                &SimulatorConfig {
                    deck_size,
                    learn_span: req.days_to_simulate as usize,
                    max_cost_perday: f32::MAX,
                    max_ivl: req.max_interval as f32,
                    learn_costs: p.learn_costs,
                    review_costs: p.review_costs,
                    first_rating_prob: p.first_rating_prob,
                    review_rating_prob: p.review_rating_prob,
                    first_rating_offsets: p.first_rating_offsets,
                    first_session_lens: p.first_session_lens,
                    forget_rating_offset: p.forget_rating_offset,
                    forget_session_len: p.forget_session_len,
                    loss_aversion: req.loss_aversion as f32,
                    learn_limit,
                    review_limit: usize::MAX,
                    new_cards_ignore_review_limit: true,
                    suspend_after_lapses: None,
                    post_scheduling_fn,
                    review_priority_fn: None,
                },
                &req.params,
                |ip| {
                    anki_progress
                        .update(false, |p| {
                            p.current = ip.current as u32;
                        })
                        .is_ok()
                },
            )?
            .clamp(0.7, 0.95))
    }

    pub fn get_optimal_retention_parameters(
        &mut self,
        revlogs: Vec<RevlogEntry>,
    ) -> Result<SimulatorConfig> {
        let fsrs_revlog: Vec<fsrs::RevlogEntry> = revlogs.into_iter().map(|r| r.into()).collect();
        let params =
            extract_simulator_config(fsrs_revlog, self.timing_today()?.next_day_at.into(), true);
        Ok(params)
    }
}

impl From<crate::revlog::RevlogReviewKind> for fsrs::RevlogReviewKind {
    fn from(kind: crate::revlog::RevlogReviewKind) -> Self {
        match kind {
            crate::revlog::RevlogReviewKind::Learning => fsrs::RevlogReviewKind::Learning,
            crate::revlog::RevlogReviewKind::Review => fsrs::RevlogReviewKind::Review,
            crate::revlog::RevlogReviewKind::Relearning => fsrs::RevlogReviewKind::Relearning,
            crate::revlog::RevlogReviewKind::Filtered => fsrs::RevlogReviewKind::Filtered,
            crate::revlog::RevlogReviewKind::Manual
            | crate::revlog::RevlogReviewKind::Rescheduled => fsrs::RevlogReviewKind::Manual,
        }
    }
}

impl From<crate::revlog::RevlogEntry> for fsrs::RevlogEntry {
    fn from(entry: crate::revlog::RevlogEntry) -> Self {
        fsrs::RevlogEntry {
            id: entry.id.into(),
            cid: entry.cid.into(),
            usn: entry.usn.into(),
            button_chosen: entry.button_chosen,
            interval: entry.interval,
            last_interval: entry.last_interval,
            ease_factor: entry.ease_factor,
            taken_millis: entry.taken_millis,
            review_kind: entry.review_kind.into(),
        }
    }
}
