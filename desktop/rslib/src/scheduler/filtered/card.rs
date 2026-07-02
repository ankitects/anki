// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::DeckFilterContext;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::prelude::*;
use crate::scheduler::timing::is_unix_epoch_timestamp;

impl Card {
    pub(crate) fn restore_queue_from_type(&mut self) {
        self.queue = match self.ctype {
            CardType::Learn | CardType::Relearn => {
                if is_unix_epoch_timestamp(self.due) {
                    // unix timestamp
                    CardQueue::Learn
                } else {
                    // day number
                    CardQueue::DayLearn
                }
            }
            CardType::New => CardQueue::New,
            CardType::Review => CardQueue::Review,
        }
    }

    pub(crate) fn move_into_filtered_deck(&mut self, ctx: &DeckFilterContext, position: i32) {
        // filtered and v1 learning cards are excluded, so odue should be guaranteed to
        // be zero
        if self.original_due != 0 {
            println!("bug: odue was set");
            return;
        }

        self.original_deck_id = self.deck_id;
        self.deck_id = ctx.target_deck;

        self.original_due = self.due;

        // if rescheduling is disabled, all cards go in the review queue
        if !ctx.config.reschedule {
            self.queue = CardQueue::Review;
        }
        if self.due > 0 {
            self.due = position;
        }
    }

    /// Restores to the original deck and clears original_due.
    /// This does not update the queue or type, so should only be used as
    /// part of an operation that adjusts those separately.
    pub(crate) fn remove_from_filtered_deck_before_reschedule(&mut self) {
        if self.original_deck_id.0 != 0 {
            self.deck_id = self.original_deck_id;
            self.original_deck_id.0 = 0;
            self.original_due = 0;
        }
    }

    pub(crate) fn original_or_current_deck_id(&self) -> DeckId {
        self.original_deck_id.or(self.deck_id)
    }

    pub(crate) fn remove_from_filtered_deck_restoring_queue(&mut self) {
        if self.original_deck_id.0 == 0 {
            // not in a filtered deck
            return;
        }

        self.deck_id = self.original_deck_id;
        self.original_deck_id.0 = 0;

        if self.original_due != 0 {
            self.due = self.original_due;
        }

        if (self.queue as i8) >= 0 {
            self.restore_queue_from_type();
        }

        self.original_due = 0;
    }

    pub(crate) fn is_filtered(&self) -> bool {
        self.original_deck_id.0 > 0
    }
}
