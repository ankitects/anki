// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{DueCard, NewCard, QueueBuilder};
use crate::{
    deckconfig::NewCardGatherPriority, prelude::*, scheduler::queue::DueCardKind,
    storage::card::NewCardSorting,
};

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
                        false
                    } else {
                        if let Some(node_id) = self.limits.remaining_node_id(card.current_deck_id) {
                            if self.add_due_card(card) {
                                self.limits
                                    .decrement_node_and_parent_limits(&node_id, false);
                            }
                        }
                        true
                    }
                },
            )?;
        }
        Ok(())
    }

    fn gather_new_cards(&mut self, col: &mut Collection) -> Result<()> {
        match self.context.sort_options.new_gather_priority {
            NewCardGatherPriority::Deck => self.gather_new_cards_by_deck(col),
            NewCardGatherPriority::LowestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::HighestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::HighestPosition)
            }
            NewCardGatherPriority::RandomNotes => self.gather_new_cards_sorted(
                col,
                NewCardSorting::RandomNotes(self.context.timing.days_elapsed),
            ),
            NewCardGatherPriority::RandomCards => self.gather_new_cards_sorted(
                col,
                NewCardSorting::RandomCards(self.context.timing.days_elapsed),
            ),
        }
    }

    fn gather_new_cards_by_deck(&mut self, col: &mut Collection) -> Result<()> {
        for deck_id in self.limits.active_decks() {
            if self.limits.root_limit_reached() {
                break;
            }
            if !self.limits.limit_reached(deck_id) {
                col.storage.for_each_new_card_in_deck(deck_id, |card| {
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

    fn gather_new_cards_sorted(
        &mut self,
        col: &mut Collection,
        order: NewCardSorting,
    ) -> Result<()> {
        col.storage
            .for_each_new_card_in_active_decks(order, |card| {
                if self.limits.root_limit_reached() {
                    false
                } else {
                    if let Some(node_id) = self.limits.remaining_node_id(card.current_deck_id) {
                        if self.add_new_card(card) {
                            self.limits.decrement_node_and_parent_limits(&node_id, true);
                        }
                    }
                    true
                }
            })?;

        Ok(())
    }

    /// True if limit should be decremented.
    fn add_due_card(&mut self, card: DueCard) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.into())
            .map(|mode| match card.kind {
                DueCardKind::Review => mode.bury_reviews,
                DueCardKind::Learning => mode.bury_interday_learning,
            })
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
    fn add_new_card(&mut self, card: NewCard) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.into())
            .map(|mode| mode.bury_new)
            .unwrap_or_default();
        // no previous siblings seen?
        if bury_this_card {
            false
        } else {
            self.new.push(card);
            true
        }
    }
}
