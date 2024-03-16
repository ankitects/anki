use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use fsrs::{SimulatorConfig,simulate};
use itertools::Itertools;

use crate::prelude::*;

impl Collection {
    pub fn simulate_review(
        &mut self,
        req: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsReviewResponse> {
        let p = self.get_optimal_retention_parameters(&req.search)?;
        let desired_retention = 0.9;
        let config = SimulatorConfig {
            deck_size: req.deck_size as usize,
            learn_span: req.days_to_simulate as usize,
            max_cost_perday: f64::MAX,
            max_ivl: req.max_interval as f64,
            recall_costs: [p.recall_secs_hard, p.recall_secs_good, p.recall_secs_easy],
            forget_cost: p.forget_secs,
            learn_cost: p.learn_secs,
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
            loss_aversion: 1.0,
            learn_limit: req.new_limit as usize,
            review_limit: req.review_limit as usize,
        };
        let (
            accumulated_knowledge_acquisition,
            daily_review_count,
            daily_new_count,
            daily_time_cost,
        ) = simulate(
            &config,
            &req.weights.iter().map(|w| *w as f64).collect_vec(),
            desired_retention,
            None,
            None, // TODO: query cards reviewed in the deck and convert them into fsrs::Card
        );
        Ok(SimulateFsrsReviewResponse {
            accumulated_knowledge_acquisition: accumulated_knowledge_acquisition
                .iter()
                .map(|x| *x as f32)
                .collect_vec(),
            daily_review_count: daily_review_count.iter().map(|x| *x as u32).collect_vec(),
            daily_new_count: daily_new_count.iter().map(|x| *x as u32).collect_vec(),
            daily_time_cost: daily_time_cost.iter().map(|x| *x as f32).collect_vec(),
        })
    }
}
