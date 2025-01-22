// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use fsrs::simulate;
use fsrs::SimulatorConfig;
use itertools::Itertools;

use crate::card::CardQueue;
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
        let days_elapsed = self.timing_today().unwrap().days_elapsed as i32;
        let mut converted_cards = cards
            .into_iter()
            .filter(|c| c.queue != CardQueue::Suspended && c.queue != CardQueue::PreviewRepeat && c.queue != CardQueue::New)
            .filter_map(|c| Card::convert(c, days_elapsed, req.days_to_simulate))
            .collect_vec();
        let introduced_today_count = self.search_cards(&format!("{} introduced:1", &req.search), SortMode::NoOrder)?.len();
        if req.new_limit > 0 {
            let new_cards = (introduced_today_count..(req.deck_size as usize + introduced_today_count)).map(|i| fsrs::Card {
                difficulty: f32::NEG_INFINITY,
                stability: 1e-8, // Hack to get around the filter
                last_date: f32::NEG_INFINITY,
                due: 1. + (i / req.new_limit as usize) as f32,
            });
            dbg!(introduced_today_count, req.deck_size as usize, converted_cards.len(), new_cards.clone().collect_vec());
            converted_cards.extend(new_cards);
        }
        dbg!(&converted_cards);
        let p = self.get_optimal_retention_parameters(revlogs)?;
        let config = SimulatorConfig {
            deck_size: converted_cards.len(),
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
            loss_aversion: 1.0,
            learn_limit: req.new_limit as usize,
            review_limit: req.review_limit as usize,
            new_cards_ignore_review_limit: req.new_cards_ignore_review_limit,
        };
        let result = simulate(
            &config,
            &req.params,
            req.desired_retention,
            None,
            Some(converted_cards),
        )?;
        Ok(SimulateFsrsReviewResponse {
            accumulated_knowledge_acquisition: result.memorized_cnt_per_day.to_vec(),
            daily_review_count: result
                .review_cnt_per_day
                .iter()
                .map(|x| *x as u32)
                .collect_vec(),
            daily_new_count: result
                .learn_cnt_per_day
                .iter()
                .map(|x| *x as u32)
                .collect_vec(),
            daily_time_cost: result.cost_per_day.to_vec(),
        })
    }
}

impl Card {
    fn convert(card: Card, days_elapsed: i32, day_to_simulate: u32) -> Option<fsrs::Card> {
        match card.memory_state {
            Some(state) => match card.queue {
                CardQueue::DayLearn | CardQueue::Review => {
                    let due = card.original_or_current_due();
                    let relative_due = due - days_elapsed;
                    let last_date = (relative_due - card.interval as i32).min(0) as f32;
                    Some(fsrs::Card {
                        difficulty: state.difficulty,
                        stability: state.stability,
                        last_date,
                        due: relative_due as f32,
                    })
                }
                CardQueue::New => Some(fsrs::Card {
                    difficulty: 1e-10,
                    stability: 1e-10,
                    last_date: 0.0,
                    due: day_to_simulate as f32,
                }),
                CardQueue::Learn | CardQueue::SchedBuried | CardQueue::UserBuried => {
                    Some(fsrs::Card {
                        difficulty: state.difficulty,
                        stability: state.stability,
                        last_date: 0.0,
                        due: 0.0,
                    })
                }
                CardQueue::PreviewRepeat => None,
                CardQueue::Suspended => None,
            },
            None => Some(fsrs::Card {
                difficulty: 1e-10,
                stability: 1e-10,
                last_date: 0.0,
                due: day_to_simulate as f32,
            }),
        }
    }
}
