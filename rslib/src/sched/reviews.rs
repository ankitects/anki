// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    deckconf::INITIAL_EASE_FACTOR_THOUSANDS,
    err::Result,
    prelude::AnkiError,
};
use lazy_static::lazy_static;
use rand::distributions::{Distribution, Uniform};
use regex::Regex;

impl Card {
    /// Make card due in `days_from_today`.
    /// If card is not a review card, convert it into one.
    /// Relearning cards have their interval preserved. Normal review
    /// cards have their interval adjusted based on change between the
    /// previous and new due date.
    fn set_due_date(&mut self, today: u32, days_from_today: u32, force_reset: bool) {
        let new_due = (today + days_from_today) as i32;
        let new_interval = if force_reset {
            days_from_today
        } else if let Some(old_due) = self.current_review_due_day() {
            // review cards have their interval shifted based on actual elapsed time
            let days_early = old_due - new_due;
            ((self.interval as i32) - days_early).max(0) as u32
        } else if self.ctype == CardType::Relearn {
            // We can't know how early or late this card entered relearning
            // without consulting the revlog, which may not exist. If the user
            // has their deck set up to reduce but not zero the interval on
            // failure, the card may potentially have an interval of weeks or
            // months, so we'll favour that if it's larger than the chosen
            // `days_from_today`
            self.interval.max(days_from_today)
        } else {
            // other cards are given a new starting interval
            days_from_today
        };

        self.schedule_as_review(new_interval, new_due);
    }

    // For review cards not in relearning, return the day the card is due.
    fn current_review_due_day(&self) -> Option<i32> {
        match self.ctype {
            CardType::New | CardType::Learn | CardType::Relearn => None,
            CardType::Review => Some(self.original_or_current_due()),
        }
    }

    fn schedule_as_review(&mut self, interval: u32, due: i32) {
        self.remove_from_filtered_deck_before_reschedule();
        self.interval = interval.max(1);
        self.due = due;
        self.ctype = CardType::Review;
        self.queue = CardQueue::Review;
        if self.ease_factor == 0 {
            // unlike the old Python code, we leave the ease factor alone
            // if it's already set
            self.ease_factor = INITIAL_EASE_FACTOR_THOUSANDS;
        }
    }
}

#[derive(Debug, PartialEq)]
pub struct DueDateSpecifier {
    min: u32,
    max: u32,
    force_reset: bool,
}

pub fn parse_due_date_str(s: &str) -> Result<DueDateSpecifier> {
    lazy_static! {
        static ref RE: Regex = Regex::new(
            r#"(?x)^
            # a number
            (?P<min>\d+)
            # an optional hypen and another number
            (?:
                -
                (?P<max>\d+)
            )?
            # optional exclamation mark
            (?P<bang>!)?
            $
        "#
        )
        .unwrap();
    }
    let caps = RE.captures(s).ok_or_else(|| AnkiError::invalid_input(s))?;
    let min: u32 = caps.name("min").unwrap().as_str().parse()?;
    let max = if let Some(max) = caps.name("max") {
        max.as_str().parse()?
    } else {
        min
    };
    let force_reset = caps.name("bang").is_some();
    Ok(DueDateSpecifier {
        min: min.min(max),
        max: max.max(min),
        force_reset,
    })
}

impl Collection {
    pub fn set_due_date(&mut self, cids: &[CardID], spec: DueDateSpecifier) -> Result<()> {
        let usn = self.usn()?;
        let today = self.timing_today()?.days_elapsed;
        let mut rng = rand::thread_rng();
        let distribution = Uniform::from(spec.min..=spec.max);
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, false)?;
            for mut card in col.storage.all_searched_cards()? {
                let original = card.clone();
                let days_from_today = distribution.sample(&mut rng);
                card.set_due_date(today, days_from_today, spec.force_reset);
                col.log_manually_scheduled_review(&card, &original, usn)?;
                col.update_card(&mut card, &original, usn)?;
            }
            col.storage.clear_searched_cards_table()?;
            Ok(())
        })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::prelude::*;

    #[test]
    fn parse() -> Result<()> {
        type S = DueDateSpecifier;
        assert!(parse_due_date_str("").is_err());
        assert!(parse_due_date_str("x").is_err());
        assert!(parse_due_date_str("-5").is_err());
        assert_eq!(
            parse_due_date_str("5")?,
            S {
                min: 5,
                max: 5,
                force_reset: false
            }
        );
        assert_eq!(
            parse_due_date_str("5!")?,
            S {
                min: 5,
                max: 5,
                force_reset: true
            }
        );
        assert_eq!(
            parse_due_date_str("50-70")?,
            S {
                min: 50,
                max: 70,
                force_reset: false
            }
        );
        assert_eq!(
            parse_due_date_str("70-50!")?,
            S {
                min: 50,
                max: 70,
                force_reset: true
            }
        );
        Ok(())
    }

    #[test]
    fn due_date() {
        let mut c = Card::new(NoteID(0), 0, DeckID(0), 0);

        // setting the due date of a new card will convert it
        c.set_due_date(5, 2, false);
        assert_eq!(c.ctype, CardType::Review);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);

        // reschedule it again the next day, shifting it from day 7 to day 9
        c.set_due_date(6, 3, false);
        assert_eq!(c.due, 9);
        // we moved it 2 days forward from its original 2 day interval, and the
        // interval should match the new delay
        assert_eq!(c.interval, 4);

        // we can bring cards forward too - return it to its original due date
        c.set_due_date(6, 1, false);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);

        // we can force the interval to be reset instead of shifted
        c.set_due_date(6, 2, true);
        assert_eq!(c.due, 8);
        assert_eq!(c.interval, 2);

        // should work in a filtered deck
        c.original_due = 7;
        c.original_deck_id = DeckID(1);
        c.due = -10000;
        c.queue = CardQueue::New;
        c.set_due_date(6, 1, false);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);
        assert_eq!(c.queue, CardQueue::Review);
        assert_eq!(c.original_due, 0);
        assert_eq!(c.original_deck_id, DeckID(0));

        // when relearning, a larger delay than the interval will win
        c.ctype = CardType::Relearn;
        c.original_due = c.due;
        c.due = 12345678;
        c.set_due_date(6, 10, false);
        assert_eq!(c.due, 16);
        assert_eq!(c.interval, 10);

        // but a shorter delay will preserve the current interval
        c.ctype = CardType::Relearn;
        c.original_due = c.due;
        c.due = 12345678;
        c.set_due_date(6, 1, false);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 10);
    }
}
