// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{BuryMode, Context, DueCard, NewCard, QueueBuilder};
use crate::{deckconfig::NewCardGatherPriority, prelude::*, scheduler::queue::DueCardKind};

enum DueOrNewCard {
    Due(DueCard),
    New(NewCard),
}

impl DueOrNewCard {
    fn original_deck_id(&self) -> DeckId {
        match self {
            Self::Due(card) => card.original_deck_id.or(card.current_deck_id),
            Self::New(card) => card.original_deck_id.or(card.current_deck_id),
        }
    }

    fn note_id(&self) -> NoteId {
        match self {
            Self::Due(card) => card.note_id,
            Self::New(card) => card.note_id,
        }
    }
}

impl From<DueCard> for DueOrNewCard {
    fn from(card: DueCard) -> DueOrNewCard {
        DueOrNewCard::Due(card)
    }
}

impl From<NewCard> for DueOrNewCard {
    fn from(card: NewCard) -> DueOrNewCard {
        DueOrNewCard::New(card)
    }
}

impl Context {
    pub(super) fn bury_mode(&self, deck_id: DeckId) -> BuryMode {
        self.deck_map
            .get(&deck_id)
            .and_then(|deck| deck.config_id())
            .and_then(|config_id| self.config_map.get(&config_id))
            .map(|config| BuryMode {
                bury_new: config.inner.bury_new,
                bury_reviews: config.inner.bury_reviews,
            })
            .unwrap_or_default()
    }
}

impl QueueBuilder {
    pub(super) fn gather_cards(&mut self, col: &mut Collection) -> Result<()> {
        self.gather_intraday_learning_cards(col)?;
        self.gather_due_cards(col, DueCardKind::Learning)?;
        self.gather_due_cards(col, DueCardKind::Review)?;
        self.limits.cap_new_to_review();
        self.gather_new_cards(col)?;

        Ok(())
    }

    fn gather_intraday_learning_cards(&mut self, col: &mut Collection) -> Result<()> {
        col.storage.for_each_intraday_card_in_active_decks(
            self.context.timing.next_day_at,
            |card| {
                self.get_and_update_bury_mode_for_note(card.into());
                self.learning.push(card);
            },
        )?;

        Ok(())
    }

    fn gather_due_cards(&mut self, col: &mut Collection, kind: DueCardKind) -> Result<()> {
        if !self.limits.root_limit_reached() {
            col.storage.for_each_due_card_in_active_decks(
                self.context.timing.days_elapsed,
                self.context.sort_options.review_order,
                kind,
                |card| {
                    if self.limits.root_limit_reached() {
                        return false;
                    }
                    if let Some(node_id) = self.limits.remaining_node_id(card.current_deck_id) {
                        if self.add_due_card(card) {
                            self.limits
                                .decrement_node_and_parent_limits(&node_id, false);
                        }
                    }

                    true
                },
            )?;
        }
        Ok(())
    }

    fn gather_new_cards(&mut self, col: &mut Collection) -> Result<()> {
        match self.context.sort_options.new_gather_priority {
            NewCardGatherPriority::Deck => self.gather_new_cards_by_deck(col),
            NewCardGatherPriority::LowestPosition => self.gather_new_cards_by_position(col, false),
            NewCardGatherPriority::HighestPosition => self.gather_new_cards_by_position(col, true),
        }
    }

    fn gather_new_cards_by_deck(&mut self, col: &mut Collection) -> Result<()> {
        // TODO: must own Vec as closure below requires unique access to ctx
        // maybe decks should not be field of Context?
        for deck_id in self.limits.remaining_decks() {
            if self.limits.root_limit_reached() {
                break;
            }
            if !self.limits.limit_reached(deck_id) {
                col.storage.for_each_new_card_in_deck(deck_id, |card| {
                    // TODO: This could be done more efficiently if we held on to the node_id
                    // and only adjusted the parent nodes after this node's limit is reached
                    if let Some(node_id) = self.limits.remaining_node_id(deck_id) {
                        if self.add_new_card(card) {
                            self.limits.decrement_node_and_parent_limits(&node_id, true);
                        }

                        true
                    } else {
                        false
                    }
                })?;
            }
        }

        Ok(())
    }

    fn gather_new_cards_by_position(&mut self, col: &mut Collection, reverse: bool) -> Result<()> {
        col.storage
            .for_each_new_card_in_active_decks(reverse, |card| {
                if let Some(node_id) = self.limits.remaining_node_id(card.current_deck_id) {
                    if self.add_new_card(card) {
                        self.limits.decrement_node_and_parent_limits(&node_id, true);
                    }

                    true
                } else {
                    false
                }
            })?;

        Ok(())
    }

    /// True if limit should be decremented.
    fn add_due_card(&mut self, card: DueCard) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.into())
            .map(|mode| mode.bury_reviews)
            .unwrap_or_default();
        if bury_this_card {
            false
        } else {
            match card.kind {
                DueCardKind::Review => self.review.push(card),
                DueCardKind::Learning => self.day_learning.push(card),
            }

            true
        }
    }

    // True if limit should be decremented.
    pub(in super::super) fn add_new_card(&mut self, card: NewCard) -> bool {
        let previous_bury_mode = self
            .get_and_update_bury_mode_for_note(card.into())
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
    fn get_and_update_bury_mode_for_note(&mut self, card: DueOrNewCard) -> Option<BuryMode> {
        let mut previous_mode = None;
        let new_mode = self.context.bury_mode(card.original_deck_id());
        self.context
            .seen_note_ids
            .entry(card.note_id())
            .and_modify(|entry| {
                previous_mode = Some(*entry);
                entry.bury_new |= new_mode.bury_new;
                entry.bury_reviews |= new_mode.bury_reviews;
            })
            .or_insert(new_mode);

        previous_mode
    }
}
