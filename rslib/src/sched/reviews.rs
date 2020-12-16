// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    deckconf::INITIAL_EASE_FACTOR_THOUSANDS,
    err::Result,
};
use rand::distributions::{Distribution, Uniform};

impl Card {
    fn schedule_as_review(&mut self, interval: u32, today: u32) {
        self.remove_from_filtered_deck_before_reschedule();
        self.interval = interval.max(1);
        self.due = (today + interval) as i32;
        self.ctype = CardType::Review;
        self.queue = CardQueue::Review;
        if self.ease_factor == 0 {
            // unlike the old Python code, we leave the ease factor alone
            // if it's already set
            self.ease_factor = INITIAL_EASE_FACTOR_THOUSANDS;
        }
    }
}

impl Collection {
    pub fn reschedule_cards_as_reviews(
        &mut self,
        cids: &[CardID],
        min_days: u32,
        max_days: u32,
    ) -> Result<()> {
        let usn = self.usn()?;
        let today = self.timing_today()?.days_elapsed;
        let mut rng = rand::thread_rng();
        let distribution = Uniform::from(min_days..=max_days);
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, false)?;
            for mut card in col.storage.all_searched_cards()? {
                let original = card.clone();
                let interval = distribution.sample(&mut rng);
                col.log_manually_scheduled_review(&card, usn, interval.max(1))?;
                card.schedule_as_review(interval, today);
                col.update_card(&mut card, &original, usn)?;
            }
            col.storage.clear_searched_cards_table()?;
            Ok(())
        })
    }
}
