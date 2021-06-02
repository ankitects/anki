// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{cmp::Ordering, hash::Hasher};

use fnv::FnvHasher;

use super::{NewCard, NewCardSortOrder, QueueBuilder};

impl QueueBuilder {
    pub(super) fn sort_new(&mut self) {
        match self.sort_options.new_order {
            NewCardSortOrder::TemplateThenDue => {
                self.new.sort_unstable_by(template_then_due);
            }
            NewCardSortOrder::TemplateThenRandom => {
                self.new.iter_mut().for_each(NewCard::hash_id_and_mtime);
                self.new.sort_unstable_by(template_then_random);
            }
            NewCardSortOrder::Due => self.new.sort_unstable_by(new_position),
            NewCardSortOrder::Random => {
                self.new.iter_mut().for_each(NewCard::hash_id_and_mtime);
                self.new.sort_unstable_by(new_hash)
            }
        }
    }
}

fn template_then_due(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, a.due).cmp(&(b.template_index, b.due))
}

fn template_then_random(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, a.hash).cmp(&(b.template_index, b.hash))
}

fn new_position(a: &NewCard, b: &NewCard) -> Ordering {
    a.due.cmp(&b.due)
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
