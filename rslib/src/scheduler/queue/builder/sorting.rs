// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::hash::Hasher;

use fnv::FnvHasher;

use super::NewCard;
use super::NewCardSortOrder;
use super::QueueBuilder;

impl QueueBuilder {
    pub(super) fn sort_new(&mut self) {
        match self.context.sort_options.new_order {
            // preserve gather order
            NewCardSortOrder::NoSort => (),
            NewCardSortOrder::Template => {
                // stable sort to preserve gather order
                self.new
                    .sort_by(|a, b| a.template_index.cmp(&b.template_index))
            }
            NewCardSortOrder::TemplateThenRandom => {
                self.hash_new_cards_by_id();
                self.new.sort_unstable_by(cmp_template_then_hash);
            }
            NewCardSortOrder::RandomNoteThenTemplate => {
                self.hash_new_cards_by_note_id();
                self.new.sort_unstable_by(cmp_hash_then_template);
            }
            NewCardSortOrder::RandomCard => {
                self.hash_new_cards_by_id();
                self.new.sort_unstable_by(cmp_hash)
            }
        }
    }

    fn hash_new_cards_by_id(&mut self) {
        self.new
            .iter_mut()
            .for_each(|card| card.hash_id_with_salt(self.context.timing.days_elapsed as i64));
    }

    fn hash_new_cards_by_note_id(&mut self) {
        self.new
            .iter_mut()
            .for_each(|card| card.hash_note_id_with_salt(self.context.timing.days_elapsed as i64));
    }
}

fn cmp_hash(a: &NewCard, b: &NewCard) -> Ordering {
    a.hash.cmp(&b.hash)
}

fn cmp_template_then_hash(a: &NewCard, b: &NewCard) -> Ordering {
    (a.template_index, a.hash).cmp(&(b.template_index, b.hash))
}

fn cmp_hash_then_template(a: &NewCard, b: &NewCard) -> Ordering {
    (a.hash, a.template_index).cmp(&(b.hash, b.template_index))
}

// We sort based on a hash so that if the queue is rebuilt, remaining
// cards come back in the same approximate order (mixing + due learning cards
// may still result in a different card)

impl NewCard {
    fn hash_id_with_salt(&mut self, salt: i64) {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(self.id.0);
        hasher.write_i64(salt);
        self.hash = hasher.finish();
    }

    fn hash_note_id_with_salt(&mut self, salt: i64) {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(self.note_id.0);
        hasher.write_i64(salt);
        self.hash = hasher.finish();
    }
}
