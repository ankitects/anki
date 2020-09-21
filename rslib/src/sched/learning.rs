// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardQueue, CardType},
    deckconf::INITIAL_EASE_FACTOR_THOUSANDS,
};

impl Card {
    /// Remove the card from the (re)learning queue.
    /// This will reset cards in learning.
    /// Only used in the V1 scheduler.
    /// Unlike the legacy Python code, this sets the due# to 0 instead of
    /// one past the previous max due number.
    pub(crate) fn remove_from_learning(&mut self) {
        if !matches!(self.queue, CardQueue::Learn | CardQueue::DayLearn) {
            return;
        }

        if self.ctype == CardType::Review {
            // reviews are removed from relearning
            self.due = self.original_due;
            self.original_due = 0;
            self.queue = CardQueue::Review;
        } else {
            // other cards are reset to new
            self.ctype = CardType::New;
            self.queue = CardQueue::New;
            self.interval = 0;
            self.due = 0;
            self.original_due = 0;
            self.ease_factor = INITIAL_EASE_FACTOR_THOUSANDS;
        }
    }

    fn all_remaining_steps(&self) -> u32 {
        self.remaining_steps % 1000
    }

    #[allow(dead_code)]
    fn remaining_steps_today(&self) -> u32 {
        self.remaining_steps / 1000
    }

    #[allow(dead_code)]
    pub(crate) fn current_learning_delay_seconds(&self, delays: &[u32]) -> Option<u32> {
        if self.queue == CardQueue::Learn {
            let remaining = self.all_remaining_steps();
            delays
                .iter()
                .nth_back(remaining.saturating_sub(0) as usize)
                .or(Some(&0))
                .map(|n| n * 60)
        } else {
            None
        }
    }
}
