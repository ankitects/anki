// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use fsrs::simulate;
use fsrs::SimulatorConfig;
use itertools::Itertools;

use crate::prelude::*;
use crate::search::SortMode;

impl Collection {
    pub fn simulate_review(
        &mut self,
        req: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsReviewResponse> {
        let guard = self.search_cards_into_table(&req.search, SortMode::NoOrder)?;
        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let cards = guard.col.storage.all_searched_cards()?;
        drop(guard);
        let p = self.get_optimal_retention_parameters(revlogs)?;
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
        let days_elapsed = self.timing_today().unwrap().days_elapsed as i32;
        let (
            accumulated_knowledge_acquisition,
            daily_review_count,
            daily_new_count,
            daily_time_cost,
        ) = simulate(
            &config,
            &req.weights.iter().map(|w| *w as f64).collect_vec(),
            req.desired_retention as f64,
            None,
            Some(
                cards
                    .into_iter()
                    .filter_map(|c| Card::convert(c, days_elapsed))
                    .collect_vec(),
            ),
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

impl Card {
    fn convert(card: Card, days_elapsed: i32) -> Option<fsrs::Card> {
        match card.memory_state {
            Some(state) => {
                let due = card.original_or_current_due();
                let relative_due = due - days_elapsed;
                Some(fsrs::Card {
                    difficulty: state.difficulty as f64,
                    stability: state.stability as f64,
                    last_date: (relative_due - card.interval as i32) as f64,
                    due: relative_due as f64,
                })
            }
            None => None,
        }
    }
}
