// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::ComputeOptimalRetentionRequest;
use anki_proto::scheduler::OptimalRetentionParameters;
use fsrs::SimulatorConfig;
use fsrs::FSRS;

use crate::prelude::*;
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
        let p = req.params.as_ref().or_invalid("missing params")?;
        Ok(fsrs.optimal_retention(
            &SimulatorConfig {
                deck_size: p.deck_size as usize,
                learn_span: p.days_to_simulate as usize,
                max_cost_perday: p.max_seconds_of_study_per_day as f64,
                max_ivl: p.max_interval as f64,
                recall_costs: [p.recall_secs_hard, p.recall_secs_good, p.recall_secs_easy],
                forget_cost: p.forget_secs as f64,
                learn_cost: p.learn_secs as f64,
                first_rating_prob: [
                    p.first_rating_probability_again,
                    p.first_rating_probability_hard,
                    p.first_rating_probability_good,
                    p.first_rating_probability_easy,
                ],
                review_rating_prob: [
                    p.review_rating_probability_hard,
                    p.review_rating_probability_good,
                    p.review_rating_probability_easy,
                ],
            },
            &req.weights,
            |ip| {
                anki_progress
                    .update(false, |p| {
                        p.total = ip.total as u32;
                        p.current = ip.current as u32;
                    })
                    .is_ok()
            },
        )? as f32)
    }

    pub fn get_optimal_retention_parameters(
        &mut self,
        search: &str,
    ) -> Result<OptimalRetentionParameters> {
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let deck_size = guard.cards as u32;

        // if you need access to cards too:
        // let cards = self.storage.all_searched_cards()?;

        let _revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_order()?;

        // todo: compute values from revlogs
        let params = OptimalRetentionParameters {
            deck_size,
            days_to_simulate: 365,
            max_seconds_of_study_per_day: 1800,
            // this should be filled in by the frontend based on their configured value
            max_interval: 0,
            recall_secs_hard: 14.0,
            recall_secs_good: 10.0,
            recall_secs_easy: 6.0,
            forget_secs: 50,
            learn_secs: 20,
            first_rating_probability_again: 0.15,
            first_rating_probability_hard: 0.2,
            first_rating_probability_good: 0.6,
            first_rating_probability_easy: 0.05,
            review_rating_probability_hard: 0.3,
            review_rating_probability_good: 0.6,
            review_rating_probability_easy: 0.1,
        };
        Ok(params)
    }
}
