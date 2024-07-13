// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::collections::HashSet;

use super::fuzz::constrained_fuzz_bounds;
use crate::card::CardId;
use crate::notes::NoteId;
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
}

impl LoadBalancerDay {
    fn add(&mut self, cid: CardId, nid: NoteId) {
        self.cards.push((cid, nid));
    }

    fn remove(&mut self, cid: CardId) {
        if let Some(index) = self.cards.iter().position(|c| c.0 == cid) {
            self.cards.swap_remove(index);
        }
    }
}

pub struct LoadBalancerContext<'a> {
    load_balancer: &'a LoadBalancer,
    note_id: Option<NoteId>,
}

impl<'a> LoadBalancerContext<'a> {
    pub fn find_interval(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        self.load_balancer
            .find_interval(interval, minimum, maximum, self.note_id)
    }
}

#[derive(Debug)]
pub struct LoadBalancer {
    days: [LoadBalancerDay; LOAD_BALANCE_DAYS],
}

impl LoadBalancer {
    pub fn new(today: u32, storage: &SqliteStorage) -> LoadBalancer {
        println!("filling load balancer cache");
        let cards = storage
            .get_all_cards_due_in_range(today, today + LOAD_BALANCE_DAYS as u32)
            .unwrap();
        let mut days = std::array::from_fn(|_| LoadBalancerDay::default());
        for (cards, cache_day) in cards.iter().zip(days.iter_mut()) {
            for card in cards {
                cache_day.add(card.0, card.1);
            }
        }

        LoadBalancer { days }
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
    ) -> u32 {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as u32 > MAX_LOAD_BALANCE_INTERVAL as u32 {
            println!(
                "load balancer: interval {} over threshold {}, not balancing",
                interval, MAX_LOAD_BALANCE_INTERVAL
            );
            return interval as u32;
        }

        let (before_days, after_days) = constrained_fuzz_bounds(interval, minimum, maximum);
        let after_days = after_days + 1; // +1 to make the range inclusive of the actual value

        // ok this looks weird but its a totally reasonable thing
        // I want to be as close to the original interval as possible
        // so this enumerates out from the center
        // i.e. 0 -1 1 -2 2 .....
        // for optimal load balancing, it might be preferable to
        // just default to the earliest date? it is how the old
        // addon used to do it...
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

        let notes_on_days = interval_days
            .iter()
            .map(|cards| {
                cards
                    .cards
                    .iter()
                    .map(|card| card.1)
                    .collect::<HashSet<_>>()
            })
            .collect::<Vec<_>>();

        // DEBUG CODE
        // this will be removed when this feature is fully ready
        // till then, its useful to see what is being done
        let mut sorted_intervals = intervals_to_check.clone();
        sorted_intervals.sort_by(|a, b| {
            let a_len = interval_days[a.0].cards.len();
            let b_len = interval_days[b.0].cards.len();

            if let Some(note_id) = note_id {
                let a_has_sibling = notes_on_days[a.0].contains(&note_id);
                let b_has_sibling = notes_on_days[b.0].contains(&note_id);

                if a_has_sibling != b_has_sibling {
                    return a_has_sibling.cmp(&b_has_sibling);
                }
            }

            match a_len.cmp(&b_len) {
                Ordering::Greater => Ordering::Greater,
                Ordering::Less => Ordering::Less,
                Ordering::Equal => a.1.abs().cmp(&b.1.abs()),
            }
        });

        for (index, interval_offset) in &sorted_intervals {
            let has_sibling = note_id
                .map(|note_id| notes_on_days[*index].contains(&note_id))
                .unwrap_or(false);
            println!(
                "{}{} index {} interval({}) + offset({}) = {} count {}",
                if has_sibling { "x" } else { " " },
                if *interval_offset == 0 { "*" } else { " " },
                index,
                interval,
                interval_offset,
                interval as i32 + interval_offset,
                interval_days[*index].cards.len()
            );
        }
        // END DEBUG CODE

        // find the day with fewest number of cards, falling back to distance from the
        // initial interval
        let interval_modifier = intervals_to_check
            .into_iter()
            .min_by(|a, b| {
                let a_len = interval_days[a.0].cards.len();
                let b_len = interval_days[b.0].cards.len();

                if let Some(note_id) = note_id {
                    let a_has_sibling = notes_on_days[a.0].contains(&note_id);
                    let b_has_sibling = notes_on_days[b.0].contains(&note_id);

                    if a_has_sibling != b_has_sibling {
                        return a_has_sibling.cmp(&b_has_sibling);
                    }
                }

                match a_len.cmp(&b_len) {
                    Ordering::Greater => Ordering::Greater,
                    Ordering::Less => Ordering::Less,
                    Ordering::Equal => a.1.abs().cmp(&b.1.abs()),
                }
            })
            .map(|interval| interval.1)
            .unwrap_or(0);

        let balanced_interval = (interval as i32 + interval_modifier) as u32;

        println!(
            "load_balancer: interval {} -> {}\n",
            interval as u32, balanced_interval
        );

        balanced_interval
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
