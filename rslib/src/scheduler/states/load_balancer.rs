// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::collections::HashSet;

use crate::decks::DeckId;
use crate::notes::NoteId;
use crate::storage::SqliteStorage;

const MAX_LOAD_BALANCE_INTERVAL: u32 = 90;
const PERCENT_BEFORE: f32 = 0.1;
const PERCENT_AFTER: f32 = 0.1;
const DAYS_MIN_BEFORE: i32 = 1;
const DAYS_MIN_AFTER: i32 = 1;
const DAYS_MAX_BEFORE: i32 = 6;
const DAYS_MAX_AFTER: i32 = 4;

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

    pub fn find_interval(&self, interval: f32) -> u32 {
        // if we're sending a card far out into the future, the need to balance is low
        if interval as u32 > MAX_LOAD_BALANCE_INTERVAL {
            return interval as u32;
        }

        // determine the range of days to check
        let before_range =
            ((interval as f32 * PERCENT_BEFORE) as i32).clamp(DAYS_MIN_BEFORE, DAYS_MAX_BEFORE);
        let after_range =
            ((interval as f32 * PERCENT_AFTER) as i32).clamp(DAYS_MIN_AFTER, DAYS_MAX_AFTER);

        let before_days = (interval as i32 - before_range).max(1);
        let after_days = interval as i32 + after_range + 1; // +1 to make the range inclusive of the actual value

        // ok this looks weird but its a totally reasonable thing
        // I want to be as close to the original interval as possible
        // so this enumerates out from the center
        // i.e. 0 -1 1 -2 2 .....
        // for optimal load balancing, it might be preferable to
        // just default to the earliest date? it is how the old
        // addon used to do it...
        let intervals_to_check = (before_days..interval as i32)
            .map(|before| before - interval as i32)
            .chain((interval as i32..after_days).map(|after| after - interval as i32))
            .enumerate()
            .collect::<Vec<_>>();

        let cards = if let Some(deck_id) = self.deck_id {
            self.storage
                .get_cards_in_deck_due_in_range(
                    self.today + before_days as u32,
                    self.today + after_days as u32,
                    deck_id,
                )
                .unwrap()
        } else {
            self.storage
                .get_all_cards_due_in_range(
                    self.today + before_days as u32,
                    self.today + after_days as u32,
                )
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

        (interval as i32 + interval_modifier) as u32
    }
}
