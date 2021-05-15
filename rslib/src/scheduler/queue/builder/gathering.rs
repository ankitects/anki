// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{BuryMode, DueCard, NewCard, QueueBuilder};
use crate::{card::CardQueue, prelude::*};

impl QueueBuilder {
    pub(in super::super) fn add_intraday_learning_card(
        &mut self,
        card: DueCard,
        bury_mode: BuryMode,
    ) {
        self.get_and_update_bury_mode_for_note(card.note_id, bury_mode);
        self.learning.push(card);
    }

    /// True if limit should be decremented.
    pub(in super::super) fn add_due_card(
        &mut self,
        queue: CardQueue,
        card: DueCard,
        bury_mode: BuryMode,
    ) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.note_id, bury_mode)
            .map(|mode| mode.bury_reviews)
            .unwrap_or_default();
        if bury_this_card {
            false
        } else {
            match queue {
                CardQueue::DayLearn => {
                    self.day_learning.push(card);
                }
                CardQueue::Review => {
                    self.review.push(card);
                }
                _ => unreachable!(),
            }

            true
        }
    }

    // True if limit should be decremented.
    pub(in super::super) fn add_new_card(&mut self, card: NewCard, bury_mode: BuryMode) -> bool {
        let previous_bury_mode = self
            .get_and_update_bury_mode_for_note(card.note_id, bury_mode)
            .map(|mode| mode.bury_new);
        // no previous siblings seen?
        if previous_bury_mode.is_none() {
            self.new.push(card);
            return true;
        }
        let bury_this_card = previous_bury_mode.unwrap();

        // Cards will be arriving in (due, card_id) order, with all
        // siblings sharing the same due number by default. In the
        // common case, card ids will match template order, and nothing
        // special is required. But if some cards have been generated
        // after the initial note creation, they will have higher card
        // ids, and the siblings will thus arrive in the wrong order.
        // Sorting by ordinal in the DB layer is fairly costly, as it
        // doesn't allow us to exit early when the daily limits have
        // been met, so we want to enforce ordering as we add instead.
        let previous_card_was_sibling_with_higher_ordinal = self
            .new
            .last()
            .map(|previous| {
                previous.note_id == card.note_id && previous.template_index > card.template_index
            })
            .unwrap_or(false);

        if previous_card_was_sibling_with_higher_ordinal {
            if bury_this_card {
                // When burying is enabled, we replace the existing sibling
                // with the lower ordinal one, and skip decrementing the limit.
                *self.new.last_mut().unwrap() = card;

                false
            } else {
                // When burying disabled, we'll want to add this card as well, but we
                // need to insert it in front of the later-ordinal card(s).
                let target_idx = self
                    .new
                    .iter()
                    .enumerate()
                    .rev()
                    .filter_map(|(idx, queued_card)| {
                        if queued_card.note_id != card.note_id
                            || queued_card.template_index < card.template_index
                        {
                            Some(idx + 1)
                        } else {
                            None
                        }
                    })
                    .next()
                    .unwrap_or(0);
                self.new.insert(target_idx, card);

                true
            }
        } else {
            // card has arrived in expected order - add if burying disabled
            if bury_this_card {
                false
            } else {
                self.new.push(card);

                true
            }
        }
    }

    /// If burying is enabled in `new_settings`, existing entry will be updated.
    /// Returns a copy made before changing the entry, so that a card with burying
    /// enabled will bury future siblings, but not itself.
    fn get_and_update_bury_mode_for_note(
        &mut self,
        note_id: NoteId,
        new_mode: BuryMode,
    ) -> Option<BuryMode> {
        let mut previous_mode = None;
        self.seen_note_ids
            .entry(note_id)
            .and_modify(|entry| {
                previous_mode = Some(*entry);
                entry.bury_new |= new_mode.bury_new;
                entry.bury_reviews |= new_mode.bury_reviews;
            })
            .or_insert(new_mode);

        previous_mode
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn new_siblings() {
        let mut builder = QueueBuilder::default();
        let cards = vec![
            NewCard {
                id: CardId(1),
                note_id: NoteId(1),
                template_index: 0,
                ..Default::default()
            },
            NewCard {
                id: CardId(2),
                note_id: NoteId(2),
                template_index: 1,
                ..Default::default()
            },
            NewCard {
                id: CardId(3),
                note_id: NoteId(2),
                template_index: 2,
                ..Default::default()
            },
            NewCard {
                id: CardId(4),
                note_id: NoteId(2),
                // lowest ordinal, should be used instead of card 2/3
                template_index: 0,
                ..Default::default()
            },
        ];

        for card in &cards {
            builder.add_new_card(
                card.clone(),
                BuryMode {
                    bury_new: true,
                    ..Default::default()
                },
            );
        }

        assert_eq!(builder.new[0].id, CardId(1));
        assert_eq!(builder.new[1].id, CardId(4));
        assert_eq!(builder.new.len(), 2);

        // with burying disabled, we should get all siblings in order
        let mut builder = QueueBuilder::default();

        for card in &cards {
            builder.add_new_card(card.clone(), Default::default());
        }

        assert_eq!(builder.new[0].id, CardId(1));
        assert_eq!(builder.new[1].id, CardId(4));
        assert_eq!(builder.new[2].id, CardId(2));
        assert_eq!(builder.new[3].id, CardId(3));
    }
}
