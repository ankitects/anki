use std::collections::HashSet;
use std::cmp::Ordering;
use crate::storage::SqliteStorage;
use crate::notes::NoteId;
//use crate::card::Card;


const MAX_LOAD_BALANCE_INTERVAL: u32 = 90;
const PERCENT_BEFORE: f32 = 0.2;
const PERCENT_AFTER: f32 = 0.2;
const DAYS_MIN_BEFORE: i32 = 1;
const DAYS_MIN_AFTER : i32 = 1;
const DAYS_MAX_BEFORE: i32 = 6;
const DAYS_MAX_AFTER : i32 = 4;


pub struct LoadBalancer<'a> {
    today: u32,
    note_id: NoteId,
    avoid_siblings: bool,
    storage: &'a SqliteStorage,
}

impl<'a> LoadBalancer<'a> {
    pub fn full_collection(today: u32, storage: &'a SqliteStorage, note_id: NoteId, avoid_siblings: bool) -> LoadBalancer<'a> {
        LoadBalancer {
            today,
            note_id,
            avoid_siblings,
            storage,
        }
    }

    pub fn find_interval(&self, interval: u32) -> u32 {
        // if we're sending a card far out into the future, the need to balance is low
        if interval > MAX_LOAD_BALANCE_INTERVAL {
            return interval;
        } 

        // determine the range of days to check
        let before_range = ((interval as f32 * PERCENT_BEFORE) as i32)
            .clamp(DAYS_MIN_BEFORE, DAYS_MAX_BEFORE);
        let after_range = ((interval as f32 * PERCENT_AFTER) as i32)
            .clamp(DAYS_MIN_AFTER, DAYS_MAX_AFTER);

        let before_days = (interval as i32 - before_range).max(1);
        let after_days = interval as i32 + after_range;

        // ok this looks weird but its a totally reasonable thing
        // I want to be as close to the original interval as possible
        // so this enumerates out from the center
        // i.e. 0 -1 1 -2 2 .....
        // for optimal load balancing, it might be preferable to
        // just default to the earliest date? it is how the old
        // addon used to do it...
        let intervals_to_check = (before_days..interval as i32)
            .map(|before| {
                before as i32 - interval as i32
            })
            .chain(
                (interval as i32..after_days)
                    .map(|after| {
                        after as i32 - interval as i32
                    })
            )
            .enumerate()
            .collect::<Vec<_>>();

        let cards = self.storage.get_all_cards_due_in_range(self.today + before_days as u32, self.today + after_days as u32).unwrap();
        let notes = cards
            .iter()
            .map(|cards| {
                cards
                    .iter()
                    .map(|card| {
                        card.1
                    })
                    .collect::<HashSet<_>>()
            })
            .collect::<Vec<_>>();

        // find the day with fewest number of cards, falling back to distance from the initial interval
        let interval_modifier = intervals_to_check
            .into_iter()
            .min_by(|a, b| {
                let a_len = cards[a.0].len();
                let b_len = cards[b.0].len();

                let a_has_sibling = notes[a.0].contains(&self.note_id);
                let b_has_sibling = notes[b.0].contains(&self.note_id);

                if self.avoid_siblings {
                    if a_has_sibling != b_has_sibling {
                        return a_has_sibling.cmp(&b_has_sibling);
                    }
                }

                match a_len.cmp(&b_len) {
                    Ordering::Greater => Ordering::Greater,
                    Ordering::Less => Ordering::Less,
                    Ordering::Equal => {
                        a.1.abs().cmp(&b.1.abs())
                    }
                }
            })
            .unwrap_or((0, 0));

        let interval_modifier = interval_modifier.1;

        
        (interval as i32 + interval_modifier) as u32
    }

}
