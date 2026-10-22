// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use chrono::Datelike;

use crate::prelude::*;
use crate::scheduler::states::fuzz::constrained_fuzz_bounds;
use crate::scheduler::states::load_balancer::build_easy_days_percentages;
use crate::scheduler::states::load_balancer::calculate_easy_days_modifiers;
use crate::scheduler::states::load_balancer::select_weighted_interval;
use crate::scheduler::states::load_balancer::EasyDay;
use crate::scheduler::states::load_balancer::LoadBalancerInterval;

pub struct Rescheduler {
    today: i32,
    next_day_at: TimestampSecs,
    due_cnt_per_day_by_preset: HashMap<DeckConfigId, HashMap<i32, usize>>,
    due_today_by_preset: HashMap<DeckConfigId, usize>,
    reviewed_today_by_preset: HashMap<DeckConfigId, usize>,
    easy_days_percentages_by_preset: HashMap<DeckConfigId, [EasyDay; 7]>,
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

        let mut due_cnt_per_day_by_preset: HashMap<DeckConfigId, HashMap<i32, usize>> =
            HashMap::new();
        for (did, due_date, count) in deck_stats {
            let deck_config_id = did_to_dcid.get(&did).or_not_found(did)?;
            due_cnt_per_day_by_preset
                .entry(*deck_config_id)
                .or_default()
                .entry(due_date)
                .and_modify(|e| *e += count)
                .or_insert(count);
        }

        let today = timing.days_elapsed as i32;
        let due_today_by_preset = due_cnt_per_day_by_preset
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
        let mut reviewed_today_by_preset: HashMap<DeckConfigId, usize> = HashMap::new();
        for (did, count) in reviewed_stats {
            if let Some(&deck_config_id) = &did_to_dcid.get(&did) {
                *reviewed_today_by_preset.entry(deck_config_id).or_default() += count;
            }
        }

        let easy_days_percentages_by_preset =
            build_easy_days_percentages(col.storage.get_deck_config_map()?)?;

        Ok(Self {
            today,
            next_day_at,
            due_cnt_per_day_by_preset,
            due_today_by_preset,
            reviewed_today_by_preset,
            easy_days_percentages_by_preset,
        })
    }

    pub fn update_due_cnt_per_day(
        &mut self,
        due_before: i32,
        due_after: i32,
        deck_config_id: DeckConfigId,
    ) {
        if let Some(counts) = self.due_cnt_per_day_by_preset.get_mut(&deck_config_id) {
            if let Some(count) = counts.get_mut(&due_before) {
                *count -= 1;
            }
            *counts.entry(due_after).or_default() += 1;
        }

        if due_before <= self.today && due_after > self.today {
            if let Some(count) = self.due_today_by_preset.get_mut(&deck_config_id) {
                *count -= 1;
            }
        }
        if due_before > self.today && due_after <= self.today {
            *self.due_today_by_preset.entry(deck_config_id).or_default() += 1;
        }
    }

    fn due_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self.due_today_by_preset.get(&deck_config_id).unwrap_or(&0)
    }

    fn reviewed_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self
            .reviewed_today_by_preset
            .get(&deck_config_id)
            .unwrap_or(&0)
    }

    /// Returns the number of days in the future that a card will be scheduled
    /// for. None if it should not be rescheduled (e.g. if it is overdue).
    pub fn find_interval(
        &self,
        interval: f32,
        minimum_interval: u32,
        maximum_interval: u32,
        days_elapsed: u32,
        deckconfig_id: DeckConfigId,
        fuzz_seed: Option<u64>,
    ) -> Option<u32> {
        let (before_days, after_days) =
            constrained_fuzz_bounds(interval, minimum_interval, maximum_interval);

        // Don't reschedule the card when it's overdue
        if after_days < days_elapsed {
            return None;
        }
        // Don't reschedule the card to the past
        let before_days = before_days.max(days_elapsed);

        // Generate possible intervals and their review counts
        let possible_intervals: Vec<u32> = (before_days..=after_days).collect();
        let review_counts: Vec<usize> = possible_intervals
            .iter()
            .map(|&ivl| {
                if ivl > days_elapsed {
                    let check_due = self.today + ivl as i32 - days_elapsed as i32;
                    *self
                        .due_cnt_per_day_by_preset
                        .get(&deckconfig_id)
                        .and_then(|counts| counts.get(&check_due))
                        .unwrap_or(&0)
                } else {
                    // today's workload is the sum of backlogs, cards due today and cards reviewed
                    // today
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

        let easy_days_load = self.easy_days_percentages_by_preset.get(&deckconfig_id)?;
        let easy_days_modifier =
            calculate_easy_days_modifiers(easy_days_load, &weekdays, &review_counts);

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

        select_weighted_interval(intervals, fuzz_seed)
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use super::*;

    fn rescheduler_for_due_counts(
        deck_config_id: DeckConfigId,
        due_counts: &[(i32, usize)],
        reviewed_today: usize,
        easy_days: [EasyDay; 7],
        today: i32,
    ) -> Rescheduler {
        let due_counts_map = due_counts
            .iter()
            .fold(HashMap::new(), |mut acc, &(due, count)| {
                *acc.entry(due).or_default() += count;
                acc
            });
        let due_today = due_counts_map
            .iter()
            .filter(|(due, _)| **due <= today)
            .map(|(_, count)| *count)
            .sum();

        Rescheduler {
            today,
            next_day_at: TimestampSecs(0),
            due_cnt_per_day_by_preset: HashMap::from([(deck_config_id, due_counts_map.clone())]),
            due_today_by_preset: HashMap::from([(deck_config_id, due_today)]),
            reviewed_today_by_preset: HashMap::from([(deck_config_id, reviewed_today)]),
            easy_days_percentages_by_preset: HashMap::from([(deck_config_id, easy_days)]),
        }
    }

    #[test]
    fn rescheduler_find_interval_overdue_card_is_not_rescheduled() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        assert_eq!(
            rescheduler.find_interval(10.0, 1, 10, 20, deck_config_id, Some(1)),
            None
        );
        assert_eq!(
            rescheduler.find_interval(10.0, 1, 11, 20, deck_config_id, Some(1)),
            None
        );
    }

    #[test]
    fn rescheduler_find_interval_never_goes_to_the_past_and_stays_in_bounds() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1), (7, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        for (interval, minimum_interval, maximum_interval, days_elapsed) in [
            (10.0, 1, 11, 5),
            (4.0, 1, 3, 1),
            (50.0, 10, 90, 5),
            (10.0, 50, 5, 5),
            (5.0, 1, 5, 5),
        ] {
            let Some(result) = rescheduler.find_interval(
                interval,
                minimum_interval,
                maximum_interval,
                days_elapsed,
                deck_config_id,
                Some(42),
            ) else {
                panic!("expected interval for interval={interval}, minimum={minimum_interval}, maximum={maximum_interval}, elapsed={days_elapsed}");
            };

            assert!(
                result >= days_elapsed,
                "interval moved into the past: {result} < {days_elapsed}"
            );
            let lower = minimum_interval.min(maximum_interval);
            let upper = maximum_interval.max(minimum_interval);
            assert!(
                (lower..=upper).contains(&result),
                "interval {result} out of bounds [{lower}, {upper}]"
            );
        }
    }

    #[test]
    fn rescheduler_find_interval_is_deterministic_for_a_seed() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1), (7, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        let first = rescheduler
            .find_interval(10.0, 1, 11, 5, deck_config_id, Some(99))
            .unwrap();
        for _ in 0..10 {
            let second = rescheduler
                .find_interval(10.0, 1, 11, 5, deck_config_id, Some(99))
                .unwrap();
            assert_eq!(first, second);
        }
    }

    #[test]
    fn rescheduler_find_interval_prefers_lower_load_day() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 100), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        let mut high_load_count = 0;
        let mut low_load_count = 0;
        let n = 100;

        for seed in 0..n {
            let selected = rescheduler
                .find_interval(5.5, 5, 6, 0, deck_config_id, Some(seed))
                .unwrap();
            match selected {
                5 => high_load_count += 1,
                6 => low_load_count += 1,
                other => panic!("unexpected interval: {other}"),
            }
        }

        assert_eq!(low_load_count, n,);
        assert_eq!(high_load_count, 0,);
    }

    #[test]
    fn rescheduler_find_interval_avoids_zero_percent_easy_day_when_other_day_available() {
        let deck_config_id = DeckConfigId(1);
        let mut easy_days = [EasyDay::Normal; 7];
        easy_days[0] = EasyDay::Minimum;
        let rescheduler =
            rescheduler_for_due_counts(deck_config_id, &[(5, 1), (6, 1)], 0, easy_days, 0);

        let mut blocked_day_count = 0;
        let mut preferred_day_count = 0;
        let n = 100;

        for seed in 0..n {
            let selected = rescheduler
                .find_interval(5.5, 5, 6, 0, deck_config_id, Some(seed))
                .unwrap();
            match selected {
                5 => blocked_day_count += 1,
                6 => preferred_day_count += 1,
                other => panic!("unexpected interval: {other}"),
            }
        }

        assert_eq!(preferred_day_count, n,);
        assert_eq!(blocked_day_count, 0,);
    }

    #[test]
    fn rescheduler_find_interval_returns_none_for_missing_preset() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        assert!(rescheduler
            .find_interval(10.0, 1, 11, 5, DeckConfigId(999), Some(1))
            .is_none());
    }

    #[test]
    fn rescheduler_update_due_cnt_per_day_tracks_today_boundaries() {
        let deck_config_id = DeckConfigId(1);
        let due_before = 5;
        let due_after = 8;
        let mut rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(due_before, 1), (due_after, 1)],
            0,
            [EasyDay::Normal; 7],
            due_before,
        );

        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 1);
        rescheduler.update_due_cnt_per_day(due_before, due_after, deck_config_id);
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_before],
            0
        );
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_after],
            2
        );
        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 0);

        rescheduler.update_due_cnt_per_day(due_after, due_before, deck_config_id);
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_after],
            1
        );
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_before],
            1
        );
        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 1);
    }
}
