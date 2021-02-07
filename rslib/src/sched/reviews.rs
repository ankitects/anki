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
    fn set_due_date(&mut self, today: u32, days_from_today: u32) {
        let new_due = (today + days_from_today) as i32;
        let new_interval = if let Some(old_due) = self.current_review_due_day() {
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

/// Parse a number or range (eg '4' or '4..7') into min and max.
pub fn parse_due_date_str(s: &str) -> Result<(u32, u32)> {
    lazy_static! {
        static ref SINGLE: Regex = Regex::new(r#"^\d+$"#).unwrap();
        static ref RANGE: Regex = Regex::new(
            r#"(?x)^
            (\d+)
            \.\.
            (\d+)
            $
        "#
        )
        .unwrap();
    }
    if SINGLE.is_match(s) {
        let num: u32 = s.parse()?;
        Ok((num, num))
    } else if let Some(cap) = RANGE.captures_iter(s).next() {
        let one: u32 = cap[1].parse()?;
        let two: u32 = cap[2].parse()?;
        Ok((one.min(two), two.max(one)))
    } else {
        Err(AnkiError::ParseNumError)
    }
}

impl Collection {
    pub fn set_due_date(&mut self, cids: &[CardID], min_days: u32, max_days: u32) -> Result<()> {
        let usn = self.usn()?;
        let today = self.timing_today()?.days_elapsed;
        let mut rng = rand::thread_rng();
        let distribution = Uniform::from(min_days..=max_days);
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, false)?;
            for mut card in col.storage.all_searched_cards()? {
                let original = card.clone();
                let days_from_today = distribution.sample(&mut rng);
                card.set_due_date(today, days_from_today);
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
        assert!(parse_due_date_str("").is_err());
        assert!(parse_due_date_str("x").is_err());
        assert!(parse_due_date_str("-5").is_err());
        assert_eq!(parse_due_date_str("5")?, (5, 5));
        assert_eq!(parse_due_date_str("50..70")?, (50, 70));
        assert_eq!(parse_due_date_str("70..50")?, (50, 70));
        Ok(())
    }

    #[test]
    fn due_date() {
        let mut c = Card::new(NoteID(0), 0, DeckID(0), 0);

        // setting the due date of a new card will convert it
        c.set_due_date(5, 2);
        assert_eq!(c.ctype, CardType::Review);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);

        // reschedule it again the next day, shifting it from day 7 to day 9
        c.set_due_date(6, 3);
        assert_eq!(c.due, 9);
        // we moved it 2 days forward from its original 2 day interval, and the
        // interval should match the new delay
        assert_eq!(c.interval, 4);

        // we can bring cards forward too - return it to its original due date
        c.set_due_date(6, 1);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);

        // should work in a filtered deck
        c.original_due = 7;
        c.original_deck_id = DeckID(1);
        c.due = -10000;
        c.queue = CardQueue::New;
        c.set_due_date(6, 1);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 2);
        assert_eq!(c.queue, CardQueue::Review);
        assert_eq!(c.original_due, 0);
        assert_eq!(c.original_deck_id, DeckID(0));

        // when relearning, a larger delay than the interval will win
        c.ctype = CardType::Relearn;
        c.original_due = c.due;
        c.due = 12345678;
        c.set_due_date(6, 10);
        assert_eq!(c.due, 16);
        assert_eq!(c.interval, 10);

        // but a shorter delay will preserve the current interval
        c.ctype = CardType::Relearn;
        c.original_due = c.due;
        c.due = 12345678;
        c.set_due_date(6, 1);
        assert_eq!(c.due, 7);
        assert_eq!(c.interval, 10);
    }
}
