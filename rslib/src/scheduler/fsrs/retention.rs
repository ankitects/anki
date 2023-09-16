// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::ComputeOptimalRetentionRequest;
use fsrs::SimulatorConfig;
use fsrs::FSRS;

use crate::prelude::*;

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
        Ok(fsrs.optimal_retention(
            &SimulatorConfig {
                deck_size: req.deck_size as usize,
                learn_span: req.days_to_simulate as usize,
                max_cost_perday: req.max_seconds_of_study_per_day as f64,
                max_ivl: req.max_interval as f64,
                recall_costs: [
                    req.recall_secs_hard,
                    req.recall_secs_good,
                    req.recall_secs_easy,
                ],
                forget_cost: req.forget_secs as f64,
                learn_cost: req.learn_secs as f64,
                first_rating_prob: [
                    req.first_rating_probability_again,
                    req.first_rating_probability_hard,
                    req.first_rating_probability_good,
                    req.first_rating_probability_easy,
                ],
                review_rating_prob: [
                    req.review_rating_probability_hard,
                    req.review_rating_probability_good,
                    req.review_rating_probability_easy,
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
}
