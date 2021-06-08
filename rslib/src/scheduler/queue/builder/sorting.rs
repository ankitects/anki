// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{cmp::Ordering, hash::Hasher};

use fnv::FnvHasher;

use super::{NewCard, NewCardSortOrder, QueueBuilder};

impl QueueBuilder {
    pub(super) fn sort_new(&mut self) {
        match self.sort_options.new_order {
            NewCardSortOrder::TemplateThenLowestPosition => {
                self.new.sort_unstable_by(template_then_lowest_position);
            }
            NewCardSortOrder::TemplateThenHighestPosition => {
                self.new.sort_unstable_by(template_then_highest_position);
            }
            NewCardSortOrder::TemplateThenRandom => {
                self.new.iter_mut().for_each(NewCard::hash_id_and_mtime);
                self.new.sort_unstable_by(template_then_random);
            }
            NewCardSortOrder::LowestPosition => self.new.sort_unstable_by(lowest_position),
            NewCardSortOrder::HighestPosition => self.new.sort_unstable_by(highest_position),
            NewCardSortOrder::Random => {
                self.new.iter_mut().for_each(NewCard::hash_id_and_mtime);
                self.new.sort_unstable_by(new_hash)
            }
        }
    }
}

fn template_then_lowest_position(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, a.due).cmp(&(b.template_index, b.due))
}

fn template_then_highest_position(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, b.due).cmp(&(b.template_index, a.due))
}

fn template_then_random(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, a.hash).cmp(&(b.template_index, b.hash))
}

fn lowest_position(a: &NewCard, b: &NewCard) -> Ordering {
    a.due.cmp(&b.due)
}

fn highest_position(a: &NewCard, b: &NewCard) -> Ordering {
    b.due.cmp(&a.due)
}

fn new_hash(a: &NewCard, b: &NewCard) -> Ordering {
    a.hash.cmp(&b.hash)
}

// We sort based on a hash so that if the queue is rebuilt, remaining
// cards come back in the same approximate order (mixing + due learning cards
// may still result in a different card)

impl NewCard {
    fn hash_id_and_mtime(&mut self) {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(self.id.0);
        hasher.write_i64(self.mtime.0);
        self.hash = hasher.finish();
    }
}
