// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{super::limits::RemainingLimits, DueCard, NewCard, QueueBuilder};
use crate::{card::CardQueue, prelude::*};

impl QueueBuilder {
    /// Assumes cards will arrive sorted in (queue, due) order, so learning
    /// cards come first, and reviews come before day-learning and preview cards.
    pub(in super::super) fn add_due_card(
        &mut self,
        limit: &mut RemainingLimits,
        queue: CardQueue,
        card: DueCard,
    ) -> bool {
        let should_add = self.should_add_review_card(card.note_id);

        match queue {
            CardQueue::Learn | CardQueue::PreviewRepeat => self.learning.push(card),
            CardQueue::DayLearn => {
                self.day_learning.push(card);
            }
            CardQueue::Review => {
                if should_add {
                    self.review.push(card);
                    limit.review -= 1;
                }
            }
            CardQueue::New
            | CardQueue::Suspended
            | CardQueue::SchedBuried
            | CardQueue::UserBuried => {
                unreachable!()
            }
        }

        limit.review != 0
    }

    pub(in super::super) fn add_new_card(
        &mut self,
        limit: &mut RemainingLimits,
        card: NewCard,
    ) -> bool {
        let already_seen = self.have_seen_note_id(card.note_id);
        if !already_seen {
            self.new.push(card);
            limit.new -= 1;
            return limit.new != 0;
        }

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
            .map(|previous| previous.note_id == card.note_id && previous.extra > card.extra)
            .unwrap_or(false);

        if previous_card_was_sibling_with_higher_ordinal {
            if self.bury_new {
                // When burying is enabled, we replace the existing sibling
                // with the lower ordinal one.
                *self.new.last_mut().unwrap() = card;
            } else {
                // When burying disabled, we'll want to add this card as well, but
                // not at the end of the list.
                let target_idx = self
                    .new
                    .iter()
                    .enumerate()
                    .rev()
                    .filter_map(|(idx, queued_card)| {
                        if queued_card.note_id != card.note_id || queued_card.extra < card.extra {
                            Some(idx + 1)
                        } else {
                            None
                        }
                    })
                    .next()
                    .unwrap_or(0);
                self.new.insert(target_idx, card);
                limit.new -= 1;
            }
        } else {
            // card has arrived in expected order - add if burying disabled
            if !self.bury_new {
                self.new.push(card);
                limit.new -= 1;
            }
        }

        limit.new != 0
    }

    fn should_add_review_card(&mut self, note_id: NoteID) -> bool {
        !self.have_seen_note_id(note_id) || !self.bury_reviews
    }

    /// Mark note seen, and return true if seen before.
    fn have_seen_note_id(&mut self, note_id: NoteID) -> bool {
        !self.seen_note_ids.insert(note_id)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn new_siblings() {
        let mut builder = QueueBuilder {
            bury_new: true,
            ..Default::default()
        };
        let mut limits = RemainingLimits {
            review: 0,
            new: 100,
        };

        let cards = vec![
            NewCard {
                id: CardID(1),
                note_id: NoteID(1),
                extra: 0,
                ..Default::default()
            },
            NewCard {
                id: CardID(2),
                note_id: NoteID(2),
                extra: 1,
                ..Default::default()
            },
            NewCard {
                id: CardID(3),
                note_id: NoteID(2),
                extra: 2,
                ..Default::default()
            },
            NewCard {
                id: CardID(4),
                note_id: NoteID(2),
                extra: 0,
                ..Default::default()
            },
        ];

        for card in &cards {
            builder.add_new_card(&mut limits, card.clone());
        }

        assert_eq!(builder.new[0].id, CardID(1));
        assert_eq!(builder.new[1].id, CardID(4));
        assert_eq!(builder.new.len(), 2);

        // with burying disabled, we should get all siblings in order
        builder.bury_new = false;
        builder.new.truncate(0);

        for card in &cards {
            builder.add_new_card(&mut limits, card.clone());
        }

        assert_eq!(builder.new[0].id, CardID(1));
        assert_eq!(builder.new[1].id, CardID(4));
        assert_eq!(builder.new[2].id, CardID(2));
        assert_eq!(builder.new[3].id, CardID(3));
    }
}
