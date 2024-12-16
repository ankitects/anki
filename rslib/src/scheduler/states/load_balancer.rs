// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;

use chrono::Datelike;
use rand::distributions::Distribution;
use rand::distributions::WeightedIndex;
use rand::rngs::StdRng;
use rand::SeedableRng;

use super::fuzz::constrained_fuzz_bounds;
use crate::card::CardId;
use crate::deckconfig::DeckConfigId;
use crate::error::InvalidInputError;
use crate::notes::NoteId;
use crate::prelude::*;
use crate::storage::SqliteStorage;

const MAX_LOAD_BALANCE_INTERVAL: usize = 90;
// due to the nature of load balancing, we may schedule things in the future and
// so need to keep more than just the `MAX_LOAD_BALANCE_INTERVAL` days in our
// cache. a flat 10% increase over the max interval should be enough to not have
// problems
const LOAD_BALANCE_DAYS: usize = (MAX_LOAD_BALANCE_INTERVAL as f32 * 1.1) as usize;
const SIBLING_PENALTY: f32 = 0.001;

#[derive(Debug, Default)]
struct LoadBalancerDay {
    cards: Vec<(CardId, NoteId)>,
    notes: HashSet<NoteId>,
}

impl LoadBalancerDay {
    fn add(&mut self, cid: CardId, nid: NoteId) {
        self.cards.push((cid, nid));
        self.notes.insert(nid);
    }

    fn remove(&mut self, cid: CardId) {
        if let Some(index) = self.cards.iter().position(|c| c.0 == cid) {
            let (_, rnid) = self.cards.swap_remove(index);

            // if all cards of a note are removed, remove note
            if !self.cards.iter().any(|(_cid, nid)| *nid == rnid) {
                self.notes.remove(&rnid);
            }
        }
    }

    fn has_sibling(&self, nid: &NoteId) -> bool {
        self.notes.contains(nid)
    }
}

pub struct LoadBalancerContext<'a> {
    load_balancer: &'a LoadBalancer,
    note_id: Option<NoteId>,
    deckconfig_id: DeckConfigId,
    fuzz_seed: Option<u64>,
}

impl<'a> LoadBalancerContext<'a> {
    pub fn find_interval(&self, interval: f32, minimum: u32, maximum: u32) -> Option<u32> {
        self.load_balancer.find_interval(
            interval,
            minimum,
            maximum,
            self.deckconfig_id,
            self.fuzz_seed,
            self.note_id,
        )
    }

    pub fn set_fuzz_seed(mut self, fuzz_seed: Option<u64>) -> Self {
        self.fuzz_seed = fuzz_seed;
        self
    }
}

#[derive(Debug)]
pub struct LoadBalancer {
    /// Load balancer operates at the preset level, it only counts
    /// cards in the same preset as the card being balanced.
    days_by_preset: HashMap<DeckConfigId, [LoadBalancerDay; LOAD_BALANCE_DAYS]>,
    easy_days_percentages_by_preset: HashMap<DeckConfigId, [f32; 7]>,
    next_day_at: TimestampSecs,
}

impl LoadBalancer {
    pub fn new(
        today: u32,
        did_to_dcid: HashMap<DeckId, DeckConfigId>,
        next_day_at: TimestampSecs,
        storage: &SqliteStorage,
    ) -> Result<LoadBalancer> {
        let cards_on_each_day =
            storage.get_all_cards_due_in_range(today, today + LOAD_BALANCE_DAYS as u32)?;
        let days_by_preset = cards_on_each_day
            .into_iter()
            // for each day, group all cards on each day by their deck config id
            .map(|cards_on_day| {
                cards_on_day
                    .into_iter()
                    .filter_map(|(cid, nid, did)| Some((cid, nid, did_to_dcid.get(&did)?)))
                    .fold(
                        HashMap::<_, Vec<_>>::new(),
                        |mut day_group_by_dcid, (cid, nid, dcid)| {
                            day_group_by_dcid.entry(dcid).or_default().push((cid, nid));

                            day_group_by_dcid
                        },
                    )
            })
            .enumerate()
            // consolidate card by day groups into groups of [LoadBalancerDay; LOAD_BALANCE_DAYS]s
            .fold(
                HashMap::new(),
                |mut deckconfig_group, (day_index, days_grouped_by_dcid)| {
                    for (group, cards) in days_grouped_by_dcid.into_iter() {
                        let day = deckconfig_group
                            .entry(*group)
                            .or_insert_with(|| std::array::from_fn(|_| LoadBalancerDay::default()));

                        for (cid, nid) in cards {
                            day[day_index].add(cid, nid);
                        }
                    }

                    deckconfig_group
                },
            );
        let configs = storage.get_deck_config_map()?;

        let mut easy_days_percentages_by_preset = HashMap::with_capacity(configs.len());
        for (dcid, conf) in configs {
            let easy_days_percentages = if conf.inner.easy_days_percentages.is_empty() {
                [1.0; 7]
            } else {
                conf.inner.easy_days_percentages.try_into().map_err(|_| {
                    AnkiError::from(InvalidInputError {
                        message: "expected 7 days".into(),
                        source: None,
                        backtrace: None,
                    })
                })?
            };
            easy_days_percentages_by_preset.insert(dcid, easy_days_percentages);
        }

        Ok(LoadBalancer {
            days_by_preset,
            easy_days_percentages_by_preset,
            next_day_at,
        })
    }

    pub fn review_context(
        &self,
        note_id: Option<NoteId>,
        deckconfig_id: DeckConfigId,
    ) -> LoadBalancerContext {
        LoadBalancerContext {
            load_balancer: self,
            note_id,
            deckconfig_id,
            fuzz_seed: None,
        }
    }

    /// The main load balancing function
    /// Given an interval and min/max range it does its best to find the best
    /// day within the standard fuzz range to schedule a card that leads to
    /// a consistent workload.
    ///
    /// It works by using a weighted random, assigning a weight between 0.0 and
    /// 1.0 to each day in the fuzz range for an interval.
    /// the weight takes into account the number of cards due on a day as well
    /// as the interval itself.
    /// `weight = (1 / (cards_due))**2 * (1 / target_interval)`
    ///
    /// By including the target_interval in the calculation, the interval is
    /// slightly biased to be due earlier. Without this, the load balancer
    /// ends up being very biased towards later days, especially around
    /// graduating intervals.
    ///
    /// if a note_id is provided, it attempts to avoid placing a card on a day
    /// that already has that note_id (aka avoid siblings)
    fn find_interval(
        &self,
        interval: f32,
        minimum: u32,
        maximum: u32,
        deckconfig_id: DeckConfigId,
        fuzz_seed: Option<u64>,
        note_id: Option<NoteId>,
    ) -> Option<u32> {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as usize > MAX_LOAD_BALANCE_INTERVAL
            || minimum as usize > MAX_LOAD_BALANCE_INTERVAL
        {
            return None;
        }

        let (before_days, after_days) = constrained_fuzz_bounds(interval, minimum, maximum);

        let days = self.days_by_preset.get(&deckconfig_id)?;
        let interval_days = &days[before_days as usize..=after_days as usize];

        // calculate review counts and expected distribution
        let (review_counts, weekdays): (Vec<usize>, Vec<usize>) = interval_days
            .iter()
            .enumerate()
            .map(|(i, day)| {
                (
                    day.cards.len(),
                    interval_to_weekday(i as u32 + before_days, self.next_day_at),
                )
            })
            .unzip();
        let easy_days_percentages = self.easy_days_percentages_by_preset.get(&deckconfig_id)?;
        let percentages = weekdays
            .iter()
            .map(|&wd| easy_days_percentages[wd])
            .collect::<Vec<_>>();
        let expected_distribution = check_review_distribution(&review_counts, &percentages);

        // calculate params for each day
        let intervals_and_params = interval_days
            .iter()
            .enumerate()
            .map(|(interval_index, interval_day)| {
                let target_interval = interval_index as u32 + before_days;

                // if there is a sibling on this day, give it a very low weight
                let sibling_multiplier = note_id
                    .and_then(|note_id| {
                        interval_day
                            .has_sibling(&note_id)
                            .then_some(SIBLING_PENALTY)
                    })
                    .unwrap_or(1.0);

                let weight = match review_counts[interval_index] {
                    0 => 1.0, // if theres no cards due on this day, give it the full 1.0 weight
                    card_count => {
                        let card_count_weight = (1.0 / card_count as f32).powi(2);
                        let card_interval_weight = 1.0 / target_interval as f32;

                        card_count_weight * card_interval_weight * sibling_multiplier
                    }
                };

                (
                    target_interval,
                    weight * expected_distribution[interval_index],
                )
            })
            .collect::<Vec<_>>();

        let mut rng = StdRng::seed_from_u64(fuzz_seed?);

        let weighted_intervals =
            WeightedIndex::new(intervals_and_params.iter().map(|k| k.1)).ok()?;

        let selected_interval_index = weighted_intervals.sample(&mut rng);
        Some(intervals_and_params[selected_interval_index].0)
    }

    pub fn add_card(&mut self, cid: CardId, nid: NoteId, dcid: DeckConfigId, interval: u32) {
        if let Some(days) = self.days_by_preset.get_mut(&dcid) {
            if let Some(day) = days.get_mut(interval as usize) {
                day.add(cid, nid);
            }
        }
    }

    pub fn remove_card(&mut self, cid: CardId) {
        for (_, days) in self.days_by_preset.iter_mut() {
            for day in days.iter_mut() {
                day.remove(cid);
            }
        }
    }
}

fn interval_to_weekday(interval: u32, next_day_at: TimestampSecs) -> usize {
    let target_datetime = next_day_at
        .adding_secs((interval - 1) as i64 * 86400)
        .local_datetime()
        .unwrap();
    target_datetime.weekday().num_days_from_monday() as usize
}

fn check_review_distribution(actual_reviews: &[usize], percentages: &[f32]) -> Vec<f32> {
    if percentages.iter().sum::<f32>() == 0.0 {
        return vec![1.0; actual_reviews.len()];
    }
    let total_actual = actual_reviews.iter().sum::<usize>() as f32;
    let expected_distribution: Vec<f32> = percentages
        .iter()
        .map(|&p| p * (total_actual / percentages.iter().sum::<f32>()))
        .collect();
    expected_distribution
        .iter()
        .zip(actual_reviews.iter())
        .map(|(&e, &a)| (e - a as f32).max(0.0))
        .collect()
}
