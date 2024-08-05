// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::collections::HashSet;

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
}

impl<'a> LoadBalancerContext<'a> {
    pub fn find_interval(&self, interval: f32, minimum: u32, maximum: u32) -> Option<u32> {
        self.load_balancer
            .find_interval(interval, minimum, maximum, self.note_id)
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
        }
    }

    fn find_interval(
        &self,
        interval: f32,
        minimum: u32,
        maximum: u32,
        note_id: Option<NoteId>,
    ) -> Option<u32> {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as u32 > MAX_LOAD_BALANCE_INTERVAL as u32
            || minimum > MAX_LOAD_BALANCE_INTERVAL as u32
        {
            println!(
                "load balancer: interval/minimum {}/{} over threshold {}, not balancing",
                interval, minimum, MAX_LOAD_BALANCE_INTERVAL
            );
            return None;
        }

        let (before_days, after_days) = constrained_fuzz_bounds(interval, minimum, maximum);
        let after_days = after_days + 1; // +1 to make the range inclusive of the actual value

        // ok this looks weird but its a totally reasonable thing
        // I want to be as close to the original interval as possible
        // so this enumerates out from the center
        // i.e. 0 -1 1 -2 2 .....
        let intervals_to_check = (before_days..interval as u32)
            .map(|before| before as i32 - interval as i32)
            .chain(
                (interval as u32..after_days)
                    .filter(|i| *i >= minimum)
                    .map(|after| after as i32 - interval as i32),
            )
            .enumerate()
            .collect::<Vec<_>>();

        let interval_days = &self.days[before_days as usize..after_days as usize];

        // find the day with fewest number of cards, falling back to distance from the
        // initial interval
        let interval_modifier = intervals_to_check
            .into_iter()
            .min_by(|(a_index, a_delta), (b_index, b_delta)| {
                if let Some(note_id) = note_id {
                    let a_has_sibling = interval_days[*a_index].has_sibling(&note_id);
                    let b_has_sibling = interval_days[*b_index].has_sibling(&note_id);

                    // if one day has a sibling and the other does not, sort the
                    // sibling-less day ahead of the sibling-full day without
                    // caring about card counts
                    if a_has_sibling != b_has_sibling {
                        return a_has_sibling.cmp(&b_has_sibling);
                    }
                }

                let a_len = interval_days[*a_index].cards.len();
                let b_len = interval_days[*b_index].cards.len();

                match a_len.cmp(&b_len) {
                    Ordering::Greater => Ordering::Greater,
                    Ordering::Less => Ordering::Less,
                    Ordering::Equal => a_delta.abs().cmp(&b_delta.abs()),
                }
            })
            .map(|interval| interval.1)
            .unwrap_or(0);

        Some((interval as i32 + interval_modifier) as u32)
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
