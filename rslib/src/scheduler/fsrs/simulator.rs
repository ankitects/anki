// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::sync::Arc;

use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
use anki_proto::deck_config::deck_config::config::ReviewCardOrder::*;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use fsrs::simulate;
use fsrs::PostSchedulingFn;
use fsrs::ReviewPriorityFn;
use fsrs::SimulatorConfig;
use itertools::Itertools;
use rand::rngs::StdRng;
use rand::Rng;

use crate::card::CardQueue;
use crate::prelude::*;
use crate::scheduler::states::fuzz::constrained_fuzz_bounds;
use crate::scheduler::states::load_balancer::calculate_easy_days_modifiers;
use crate::scheduler::states::load_balancer::interval_to_weekday;
use crate::scheduler::states::load_balancer::parse_easy_days_percentages;
use crate::scheduler::states::load_balancer::select_weighted_interval;
use crate::scheduler::states::load_balancer::EasyDay;
use crate::scheduler::states::load_balancer::LoadBalancerInterval;
use crate::search::SortMode;

pub(crate) fn apply_load_balance_and_easy_days(
    interval: f32,
    max_interval: f32,
    day_elapsed: usize,
    due_cnt_per_day: &[usize],
    rng: &mut StdRng,
    next_day_at: TimestampSecs,
    easy_days_percentages: &[EasyDay; 7],
) -> f32 {
    let (lower, upper) = constrained_fuzz_bounds(interval, 1, max_interval as u32);
    let mut review_counts = vec![0; upper as usize - lower as usize + 1];

    // Fill review_counts with due counts for each interval
    let start = day_elapsed + lower as usize;
    let end = (day_elapsed + upper as usize + 1).min(due_cnt_per_day.len());
    if start < due_cnt_per_day.len() {
        let copy_len = (end - start).min(review_counts.len());
        review_counts[..copy_len].copy_from_slice(&due_cnt_per_day[start..start + copy_len]);
    }

    let possible_intervals: Vec<u32> = (lower..=upper).collect();
    let weekdays = possible_intervals
        .iter()
        .map(|interval| {
            interval_to_weekday(
                *interval,
                next_day_at.adding_secs(day_elapsed as i64 * 86400),
            )
        })
        .collect::<Vec<_>>();
    let easy_days_modifier =
        calculate_easy_days_modifiers(easy_days_percentages, &weekdays, &review_counts);

    let intervals =
        possible_intervals
            .iter()
            .enumerate()
            .map(|(interval_index, &target_interval)| LoadBalancerInterval {
                target_interval,
                review_count: review_counts[interval_index],
                sibling_modifier: 1.0,
                easy_days_modifier: easy_days_modifier[interval_index],
            });
    let fuzz_seed = rng.gen();
    select_weighted_interval(intervals, Some(fuzz_seed)).unwrap() as f32
}

fn create_review_priority_fn(
    review_order: ReviewCardOrder,
    deck_size: usize,
    params: Vec<f32>,
) -> Option<ReviewPriorityFn> {
    // Helper macro to wrap closure in ReviewPriorityFn
    macro_rules! wrap {
        ($f:expr) => {
            Some(ReviewPriorityFn(std::sync::Arc::new($f)))
        };
    }

    match review_order {
        // Ease-based ordering
        EaseAscending => wrap!(|c| -(c.difficulty * 100.0) as i32),
        EaseDescending => wrap!(|c| (c.difficulty * 100.0) as i32),

        // Interval-based ordering
        IntervalsAscending => wrap!(|c| c.interval as i32),
        IntervalsDescending => wrap!(|c| -(c.interval as i32)),
        // Retrievability-based ordering
        RetrievabilityAscending => {
            wrap!(move |c| (c.retrievability(&params) * 1000.0) as i32)
        }
        RetrievabilityDescending => {
            wrap!(move |c| -(c.retrievability(&params) * 1000.0) as i32)
        }

        // Due date ordering
        Day | DayThenDeck | DeckThenDay => {
            wrap!(|c| c.scheduled_due() as i32)
        }

        // Random ordering
        Random => {
            wrap!(move |_| rand::thread_rng().gen_range(0..deck_size) as i32)
        }

        // Not implemented yet
        Added | ReverseAdded => None,
    }
}

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
        let mut cards = guard.col.storage.all_searched_cards()?;
        drop(guard);
        fn is_included_card(c: &Card) -> bool {
            c.queue != CardQueue::Suspended
                && c.queue != CardQueue::PreviewRepeat
                && c.queue != CardQueue::New
        }
        // calculate any missing memory state
        for c in &mut cards {
            if is_included_card(c) && c.memory_state.is_none() {
                let original = c.clone();
                let new_state = self.compute_memory_state(c.id)?.state;
                c.memory_state = new_state.map(Into::into);
                self.update_card_inner(c, original, self.usn()?)?;
            }
        }
        let days_elapsed = self.timing_today().unwrap().days_elapsed as i32;
        let new_cards = cards
            .iter()
            .filter(|c| c.memory_state.is_none() || c.queue == CardQueue::New)
            .count()
            + req.deck_size as usize;
        let mut converted_cards = cards
            .into_iter()
            .filter(is_included_card)
            .filter_map(|c| Card::convert(c, days_elapsed))
            .collect_vec();
        let introduced_today_count = self
            .search_cards(&format!("{} introduced:1", &req.search), SortMode::NoOrder)?
            .len()
            .min(req.new_limit as usize);
        if req.new_limit > 0 {
            let new_cards = (0..new_cards).map(|i| fsrs::Card {
                id: -(i as i64),
                difficulty: f32::NEG_INFINITY,
                stability: 1e-8,              // Not filtered by fsrs-rs
                last_date: f32::NEG_INFINITY, // Treated as a new card in simulation
                due: ((introduced_today_count + i) / req.new_limit as usize) as f32,
                interval: f32::NEG_INFINITY,
                lapses: 0,
            });
            converted_cards.extend(new_cards);
        }
        let deck_size = converted_cards.len();
        let p = self.get_optimal_retention_parameters(revlogs)?;

        let easy_days_percentages = parse_easy_days_percentages(req.easy_days_percentages)?;
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

        let review_priority_fn = req
            .review_order
            .try_into()
            .ok()
            .and_then(|order| create_review_priority_fn(order, deck_size, req.params.clone()));

        let config = SimulatorConfig {
            deck_size,
            learn_span: req.days_to_simulate as usize,
            max_cost_perday: f32::MAX,
            max_ivl: req.max_interval as f32,
            first_rating_prob: p.first_rating_prob,
            review_rating_prob: p.review_rating_prob,
            learn_limit: req.new_limit as usize,
            review_limit: req.review_limit as usize,
            new_cards_ignore_review_limit: req.new_cards_ignore_review_limit,
            suspend_after_lapses: req.suspend_after_lapse_count,
            post_scheduling_fn,
            review_priority_fn,
            learning_step_transitions: p.learning_step_transitions,
            relearning_step_transitions: p.relearning_step_transitions,
            state_rating_costs: p.state_rating_costs,
            learning_step_count: p.learning_step_count,
            relearning_step_count: p.relearning_step_count,
        };
        let result = simulate(
            &config,
            &req.params,
            req.desired_retention,
            None,
            Some(converted_cards),
        )?;
        Ok(SimulateFsrsReviewResponse {
            accumulated_knowledge_acquisition: result.memorized_cnt_per_day,
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
            daily_time_cost: result.cost_per_day,
        })
    }
}

impl Card {
    fn convert(card: Card, days_elapsed: i32) -> Option<fsrs::Card> {
        match card.memory_state {
            Some(state) => match card.queue {
                CardQueue::DayLearn | CardQueue::Review => {
                    let due = card.original_or_current_due();
                    let relative_due = due - days_elapsed;
                    let last_date = (relative_due - card.interval as i32).min(0) as f32;
                    Some(fsrs::Card {
                        id: card.id.0,
                        difficulty: state.difficulty,
                        stability: state.stability,
                        last_date,
                        due: relative_due as f32,
                        interval: card.interval as f32,
                        lapses: card.lapses,
                    })
                }
                CardQueue::New => None,
                CardQueue::Learn | CardQueue::SchedBuried | CardQueue::UserBuried => {
                    Some(fsrs::Card {
                        id: card.id.0,
                        difficulty: state.difficulty,
                        stability: state.stability,
                        last_date: 0.0,
                        due: 0.0,
                        interval: card.interval as f32,
                        lapses: card.lapses,
                    })
                }
                CardQueue::PreviewRepeat => None,
                CardQueue::Suspended => None,
            },
            None => None,
        }
    }
}
