// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::collections::HashSet;

use super::fuzz::constrained_fuzz_bounds;
use crate::decks::DeckId;
use crate::notes::NoteId;
use crate::storage::SqliteStorage;

const MAX_LOAD_BALANCE_INTERVAL: u32 = 90;

pub struct LoadBalancer<'a> {
    today: u32,
    note_id: NoteId,
    deck_id: Option<DeckId>,
    avoid_siblings: bool,
    storage: &'a SqliteStorage,
}

impl<'a> LoadBalancer<'a> {
    pub fn new_from_collection(
        today: u32,
        storage: &'a SqliteStorage,
        note_id: NoteId,
        avoid_siblings: bool,
    ) -> LoadBalancer<'a> {
        LoadBalancer {
            today,
            note_id,
            avoid_siblings,
            storage,
            deck_id: None,
        }
    }

    pub fn new_from_deck(
        today: u32,
        storage: &'a SqliteStorage,
        note_id: NoteId,
        deck_id: DeckId,
        avoid_siblings: bool,
    ) -> LoadBalancer<'a> {
        LoadBalancer {
            today,
            note_id,
            avoid_siblings,
            storage,
            deck_id: Some(deck_id),
        }
    }

    pub fn find_interval(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as u32 > MAX_LOAD_BALANCE_INTERVAL {
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

        let cards = if let Some(deck_id) = self.deck_id {
            self.storage
                .get_cards_in_deck_due_in_range(
                    self.today + before_days,
                    self.today + after_days,
                    deck_id,
                )
                .unwrap()
        } else {
            self.storage
                .get_all_cards_due_in_range(self.today + before_days, self.today + after_days)
                .unwrap()
        };

        // table to look up if there are siblings for a card on a day
        let notes_on_days = if self.avoid_siblings {
            Some(
                cards
                    .iter()
                    .map(|cards| cards.iter().map(|card| card.1).collect::<HashSet<_>>())
                    .collect::<Vec<_>>(),
            )
        } else {
            None
        };

        // DEBUG CODE
        // this will be removed when this feature is fully ready
        // till then, its useful to see what is being done
        let mut sorted_intervals = intervals_to_check.clone();
        sorted_intervals.sort_by(|a, b| {
            let a_len = cards[a.0].len();
            let b_len = cards[b.0].len();

            if let Some(notes_on_days) = &notes_on_days {
                let a_has_sibling = notes_on_days[a.0].contains(&self.note_id);
                let b_has_sibling = notes_on_days[b.0].contains(&self.note_id);

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
            println!(
                "{}{} index {} interval({}) + offset({}) = {} count {}",
                if notes_on_days.is_some()
                    && notes_on_days.as_ref().unwrap()[*index].contains(&self.note_id)
                {
                    "x"
                } else {
                    " "
                },
                if *interval_offset == 0 { "*" } else { " " },
                index,
                interval,
                interval_offset,
                interval as i32 + interval_offset,
                cards[*index].len()
            );
        }

        // find the day with fewest number of cards, falling back to distance from the
        // initial interval
        let interval_modifier = intervals_to_check
            .into_iter()
            .min_by(|a, b| {
                let a_len = cards[a.0].len();
                let b_len = cards[b.0].len();

                if let Some(notes_on_days) = &notes_on_days {
                    let a_has_sibling = notes_on_days[a.0].contains(&self.note_id);
                    let b_has_sibling = notes_on_days[b.0].contains(&self.note_id);

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
}
