// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{DueCard, NewCard, NewCardOrder, QueueBuilder, ReviewCardOrder};
use fnv::FnvHasher;
use std::{cmp::Ordering, hash::Hasher};

impl QueueBuilder {
    pub(super) fn sort_new(&mut self) {
        match self.new_order {
            NewCardOrder::Random => {
                self.new.iter_mut().for_each(NewCard::hash_id_and_mtime);
                self.new.sort_unstable_by(shuffle_new_card);
            }
            NewCardOrder::Due => {
                self.new.sort_unstable_by(|a, b| a.due.cmp(&b.due));
            }
        }
    }

    pub(super) fn sort_reviews(&mut self) {
        self.review.iter_mut().for_each(DueCard::hash_id_and_mtime);
        self.day_learning
            .iter_mut()
            .for_each(DueCard::hash_id_and_mtime);

        match self.review_order {
            ReviewCardOrder::ShuffledByDay => {
                self.review.sort_unstable_by(shuffle_by_day);
                self.day_learning.sort_unstable_by(shuffle_by_day);
            }
            ReviewCardOrder::Shuffled => {
                self.review.sort_unstable_by(shuffle_due_card);
                self.day_learning.sort_unstable_by(shuffle_due_card);
            }
            ReviewCardOrder::IntervalsAscending => {
                self.review.sort_unstable_by(intervals_ascending);
                self.day_learning.sort_unstable_by(shuffle_due_card);
            }
            ReviewCardOrder::IntervalsDescending => {
                self.review
                    .sort_unstable_by(|a, b| intervals_ascending(b, a));
                self.day_learning.sort_unstable_by(shuffle_due_card);
            }
        }
    }
}

fn shuffle_new_card(a: &NewCard, b: &NewCard) -> Ordering {
    a.extra.cmp(&b.extra)
}

fn shuffle_by_day(a: &DueCard, b: &DueCard) -> Ordering {
    (a.due, a.hash).cmp(&(b.due, b.hash))
}

fn shuffle_due_card(a: &DueCard, b: &DueCard) -> Ordering {
    a.hash.cmp(&b.hash)
}

fn intervals_ascending(a: &DueCard, b: &DueCard) -> Ordering {
    (a.interval, a.hash).cmp(&(a.interval, b.hash))
}

// We sort based on a hash so that if the queue is rebuilt, remaining
// cards come back in the same approximate order (mixing + due learning cards
// may still result in a different card)

impl DueCard {
    fn hash_id_and_mtime(&mut self) {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(self.id.0);
        hasher.write_i64(self.mtime.0);
        self.hash = hasher.finish();
    }
}

impl NewCard {
    fn hash_id_and_mtime(&mut self) {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(self.id.0);
        hasher.write_i64(self.mtime.0);
        self.extra = hasher.finish();
    }
}
