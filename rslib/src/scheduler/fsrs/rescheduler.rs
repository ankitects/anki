// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use chrono::Datelike;
use rand::distributions::Distribution;
use rand::distributions::WeightedIndex;
use rand::rngs::StdRng;
use rand::SeedableRng;

use crate::prelude::*;
use crate::scheduler::states::fuzz::constrained_fuzz_bounds;
use crate::scheduler::states::load_balancer::build_easy_days_percentages;
use crate::scheduler::states::load_balancer::calculate_easy_days_modifiers;
use crate::scheduler::states::load_balancer::EasyDay;

pub struct Rescheduler {
    today: i32,
    next_day_at: TimestampSecs,
    due_cnt_per_day_per_deck_config: HashMap<DeckConfigId, HashMap<i32, usize>>,
    due_today_per_deck_config: HashMap<DeckConfigId, usize>,
    reviewed_today_per_deck_config: HashMap<DeckConfigId, usize>,
    deck_config_id_to_easy_days_percentages: HashMap<DeckConfigId, [EasyDay; 7]>,
}

impl Rescheduler {
    pub fn new(col: &mut Collection) -> Result<Self> {
        let timing = col.timing_today()?;
        let deck_stats = col.storage.get_deck_due_counts()?;
        let deck_map = col.storage.get_decks_map()?;
        let did_to_dcid = deck_map
            .values()
            .filter_map(|deck| Some((deck.id, deck.config_id()?)))
            .collect::<HashMap<_, _>>();

        let mut due_cnt_per_day_per_deck_config: HashMap<DeckConfigId, HashMap<i32, usize>> =
            HashMap::new();
        for (did, due_date, count) in deck_stats {
            let deck_config_id = did_to_dcid[&did];
            due_cnt_per_day_per_deck_config
                .entry(deck_config_id)
                .or_default()
                .entry(due_date)
                .and_modify(|e| *e += count)
                .or_insert(count);
        }

        let today = timing.days_elapsed as i32;
        let due_today_per_deck_config = due_cnt_per_day_per_deck_config
            .iter()
            .map(|(deck_config_id, config_dues)| {
                let due_today = config_dues
                    .iter()
                    .filter(|(&due, _)| due <= today)
                    .map(|(_, &count)| count)
                    .sum();
                (*deck_config_id, due_today)
            })
            .collect();

        let next_day_at = timing.next_day_at;
        let reviewed_stats = col.storage.studied_today_by_deck(timing.next_day_at)?;
        let mut reviewed_today_per_deck_config: HashMap<DeckConfigId, usize> = HashMap::new();
        for (did, count) in reviewed_stats {
            if let Some(&deck_config_id) = &did_to_dcid.get(&did) {
                *reviewed_today_per_deck_config
                    .entry(deck_config_id)
                    .or_default() += count;
            }
        }

        let deck_config_id_to_easy_days_percentages =
            build_easy_days_percentages(col.storage.get_deck_config_map()?)?;

        Ok(Self {
            today,
            next_day_at,
            due_cnt_per_day_per_deck_config,
            due_today_per_deck_config,
            reviewed_today_per_deck_config,
            deck_config_id_to_easy_days_percentages,
        })
    }

    pub fn update_due_cnt_per_day(
        &mut self,
        due_before: i32,
        due_after: i32,
        deck_config_id: DeckConfigId,
    ) {
        if let Some(counts) = self
            .due_cnt_per_day_per_deck_config
            .get_mut(&deck_config_id)
        {
            if let Some(count) = counts.get_mut(&due_before) {
                *count -= 1;
            }
            *counts.entry(due_after).or_default() += 1;
        }

        if due_before <= self.today && due_after > self.today {
            if let Some(count) = self.due_today_per_deck_config.get_mut(&deck_config_id) {
                *count -= 1;
            }
        }
        if due_before > self.today && due_after <= self.today {
            *self
                .due_today_per_deck_config
                .entry(deck_config_id)
                .or_default() += 1;
        }
    }

    fn due_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self
            .due_today_per_deck_config
            .get(&deck_config_id)
            .unwrap_or(&0)
    }

    fn reviewed_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self
            .reviewed_today_per_deck_config
            .get(&deck_config_id)
            .unwrap_or(&0)
    }

    pub fn find_interval(
        &self,
        interval: f32,
        minimum: u32,
        maximum: u32,
        days_elapsed: u32,
        deckconfig_id: DeckConfigId,
        fuzz_seed: Option<u64>,
    ) -> Option<u32> {
        let (before_days, after_days) = constrained_fuzz_bounds(interval, minimum, maximum);

        if after_days < days_elapsed {
            return Some(before_days.min(after_days));
        }
        let before_days = before_days.max(days_elapsed);

        // Generate possible intervals and their review counts
        let possible_intervals: Vec<u32> = (before_days..=after_days).collect();
        let review_counts: Vec<usize> = possible_intervals
            .iter()
            .map(|&ivl| {
                let check_due = self.today + ivl as i32 - days_elapsed as i32;
                if ivl > days_elapsed {
                    *self
                        .due_cnt_per_day_per_deck_config
                        .get(&deckconfig_id)
                        .and_then(|counts| counts.get(&check_due))
                        .unwrap_or(&0)
                } else {
                    self.due_today(deckconfig_id) + self.reviewed_today(deckconfig_id)
                }
            })
            .collect();
        let weekdays: Vec<usize> = possible_intervals
            .iter()
            .map(|&ivl| {
                self.next_day_at
                    .adding_secs(days_elapsed as i64 * -86400)
                    .adding_secs((ivl - 1) as i64 * 86400)
                    .local_datetime()
                    .unwrap()
                    .weekday()
                    .num_days_from_monday() as usize
            })
            .collect();

        let easy_days_load = self
            .deck_config_id_to_easy_days_percentages
            .get(&deckconfig_id)
            .cloned()
            .unwrap_or([EasyDay::Normal; 7]);

        let easy_days_modifier =
            calculate_easy_days_modifiers(&easy_days_load, &weekdays, &review_counts);

        // calculate params for each day
        let intervals_and_params = possible_intervals
            .iter()
            .enumerate()
            .map(|(interval_index, &target_interval)| {
                let weight = match review_counts[interval_index] {
                    0 => 1.0, // if theres no cards due on this day, give it the full 1.0 weight
                    card_count => {
                        let card_count_weight = (1.0 / card_count as f32).powi(2);
                        let card_interval_weight = 1.0 / target_interval as f32;

                        card_count_weight
                            * card_interval_weight
                            * easy_days_modifier[interval_index]
                    }
                };

                (target_interval, weight)
            })
            .collect::<Vec<_>>();

        let mut rng = StdRng::seed_from_u64(fuzz_seed?);

        let weighted_intervals =
            WeightedIndex::new(intervals_and_params.iter().map(|k| k.1)).ok()?;

        let selected_interval_index = weighted_intervals.sample(&mut rng);
        Some(intervals_and_params[selected_interval_index].0)
    }
}
