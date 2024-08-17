// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use rand::distributions::Distribution;
use rand::distributions::WeightedIndex;
use rand::rngs::StdRng;
use rand::SeedableRng;

use super::fuzz::constrained_fuzz_bounds;
use crate::card::CardId;
use crate::decks::DeckId;
use crate::notes::NoteId;
use crate::prelude::Result;
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
    fuzz_seed: Option<u64>,
}

impl<'a> LoadBalancerContext<'a> {
    pub fn find_interval(&self, interval: f32, minimum: u32, maximum: u32) -> Option<u32> {
        self.load_balancer
            .find_interval(interval, minimum, maximum, self.fuzz_seed, self.note_id)
    }

    pub fn set_fuzz_seed(mut self, fuzz_seed: Option<u64>) -> Self {
        self.fuzz_seed = fuzz_seed;
        self
    }
}

#[derive(Debug)]
pub struct LoadBalancer {
    days: [LoadBalancerDay; LOAD_BALANCE_DAYS],
}

impl LoadBalancer {
    pub fn new(today: u32, deck_id: DeckId, storage: &SqliteStorage) -> Result<LoadBalancer> {
        let cards_on_each_day =
            storage.get_all_cards_due_in_range(today, today + LOAD_BALANCE_DAYS as u32)?;
        let decks = storage.get_all_decks()?;

        let current_deck_config_id = decks
            .iter()
            .find(|deck| deck.id == deck_id)
            .and_then(|deck| deck.config_id());

        let cards = match current_deck_config_id {
            Some(current_deck_config_id) => {
                let decks_in_preset = decks
                    .iter()
                    .filter(|deck| deck.config_id() == Some(current_deck_config_id))
                    .map(|deck| deck.id)
                    .collect::<Vec<_>>();

                cards_on_each_day
                    .into_iter()
                    .map(|day| {
                        day.into_iter()
                            .filter(|(_, _, did)| decks_in_preset.contains(did))
                            .collect()
                    })
                    .collect()
            }
            None => cards_on_each_day,
        };

        let mut days = std::array::from_fn(|_| LoadBalancerDay::default());
        for (cards, cache_day) in cards.iter().zip(days.iter_mut()) {
            for card in cards {
                cache_day.add(card.0, card.1);
            }
        }

        Ok(LoadBalancer { days })
    }

    pub fn review_context(&self, note_id: Option<NoteId>) -> LoadBalancerContext {
        LoadBalancerContext {
            load_balancer: self,
            note_id,
            fuzz_seed: None,
        }
    }

    fn find_interval(
        &self,
        interval: f32,
        minimum: u32,
        maximum: u32,
        fuzz_seed: Option<u64>,
        note_id: Option<NoteId>,
    ) -> Option<u32> {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as u32 > MAX_LOAD_BALANCE_INTERVAL as u32
            || minimum > MAX_LOAD_BALANCE_INTERVAL as u32
        {
            return None;
        }

        let (before_days, after_days) = constrained_fuzz_bounds(interval, minimum, maximum);
        let after_days = after_days + 1; // +1 to make the range inclusive of the actual value

        let interval_days = &self.days[before_days as usize..after_days as usize];
        let intervals_and_weights = interval_days
            .iter()
            .enumerate()
            .map(|(interval_index, interval_day)| {
                let target_interval = interval_index as u32 + before_days;
                let sibling_multiplier = note_id
                    .and_then(|note_id| {
                        interval_day
                            .has_sibling(&note_id)
                            .then_some(SIBLING_PENALTY)
                    })
                    .unwrap_or(1.0);

                let weight = match interval_day.cards.len() {
                    0 => 1.0,
                    card_count => {
                        let card_count_weight = (1.0 / card_count as f32).powi(2);
                        let card_interval_weight = 1.0 / target_interval as f32;

                        card_count_weight * card_interval_weight * sibling_multiplier
                    }
                };

                (target_interval, weight)
            })
            .collect::<Vec<_>>();

        let mut rng = StdRng::seed_from_u64(fuzz_seed?);

        let weighted_intervals =
            WeightedIndex::new(intervals_and_weights.iter().map(|k| k.1)).ok()?;

        let selected_interval_index = weighted_intervals.sample(&mut rng);
        Some(intervals_and_weights[selected_interval_index].0)
    }

    pub fn add_card(&mut self, cid: CardId, nid: NoteId, interval: u32) {
        if let Some(day) = self.days.get_mut(interval as usize) {
            day.add(cid, nid);
        }
    }

    pub fn remove_card(&mut self, cid: CardId) {
        for day in self.days.iter_mut() {
            day.remove(cid);
        }
    }
}
