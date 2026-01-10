// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;

use rand::rng;
use rand::Rng;

use super::DueCard;
use super::NewCard;
use super::QueueBuilder;
use crate::deckconfig::NewCardGatherPriority;
use crate::decks::limits::LimitKind;
use crate::prelude::*;
use crate::scheduler::queue::DueCardKind;
use crate::storage::card::NewCardSorting;

impl QueueBuilder {
    pub(super) fn gather_cards(&mut self, col: &mut Collection) -> Result<()> {
        self.gather_intraday_learning_cards(col)?;
        self.gather_due_cards(col, DueCardKind::Learning)?;
        self.gather_due_cards(col, DueCardKind::Review)?;
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
        if self.limits.root_limit_reached(LimitKind::Review) {
            return Ok(());
        }
        col.storage.for_each_due_card_in_active_decks(
            self.context.timing,
            self.context.sort_options.review_order,
            kind,
            self.context.fsrs,
            |card| {
                if self.limits.root_limit_reached(LimitKind::Review) {
                    return Ok(false);
                }
                if !self
                    .limits
                    .limit_reached(card.current_deck_id, LimitKind::Review)?
                    && self.add_due_card(card)
                {
                    self.limits.decrement_deck_and_parent_limits(
                        card.current_deck_id,
                        LimitKind::Review,
                    )?;
                }
                Ok(true)
            },
        )
    }

    fn gather_new_cards(&mut self, col: &mut Collection) -> Result<()> {
        let salt = Self::knuth_salt(self.context.timing.days_elapsed);
        match self.context.sort_options.new_gather_priority {
            NewCardGatherPriority::Deck => {
                self.gather_new_cards_by_deck(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::DeckThenRandomNotes => {
                self.gather_new_cards_by_deck(col, NewCardSorting::RandomNotes(salt))
            }
            NewCardGatherPriority::InterleavedDecks => {
                self.gather_new_cards_by_interleaved_decks(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::LowestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::HighestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::HighestPosition)
            }
            NewCardGatherPriority::RandomNotes => {
                self.gather_new_cards_sorted(col, NewCardSorting::RandomNotes(salt))
            }
            NewCardGatherPriority::RandomCards => {
                self.gather_new_cards_sorted(col, NewCardSorting::RandomCards(salt))
            }
        }
    }

    fn gather_new_cards_by_deck(
        &mut self,
        col: &mut Collection,
        sort: NewCardSorting,
    ) -> Result<()> {
        for deck_id in col.storage.get_active_deck_ids_sorted()? {
            if self.limits.root_limit_reached(LimitKind::New) {
                break;
            }
            if self.limits.limit_reached(deck_id, LimitKind::New)? {
                continue;
            }
            col.storage
                .for_each_new_card_in_deck(deck_id, sort, |card| {
                    let limit_reached = self.limits.limit_reached(deck_id, LimitKind::New)?;
                    if !limit_reached && self.add_new_card(card) {
                        self.limits
                            .decrement_deck_and_parent_limits(deck_id, LimitKind::New)?;
                    }
                    Ok(!limit_reached)
                })?;
        }

        Ok(())
    }

    fn gather_new_cards_by_interleaved_decks(
        &mut self,
        col: &mut Collection,
        sort: NewCardSorting,
    ) -> Result<()> {
        struct InterleavedDeckData {
            deck_id: DeckId,
            depleted: bool,
            cards: std::iter::Peekable<std::vec::IntoIter<NewCard>>,
        }
        let mut decks: Vec<InterleavedDeckData> = vec![];
        for deck_id in col.storage.get_active_deck_ids_sorted()? {
            let x: std::iter::Peekable<std::vec::IntoIter<NewCard>> = col
                .storage
                .new_cards_in_deck(deck_id, sort)?
                .into_iter()
                .peekable();
            decks.push(InterleavedDeckData {
                deck_id,
                depleted: false,
                cards: x,
            });
        }
        let mut rng = rng();
        let mut do_continue = true;
        while do_continue {
            do_continue = false;
            let mut non_depleted_decks = 0;
            // We attempt to predict how many cards that could be
            // pulled, but there are complications because decks can
            // limit decks under them in the hierarchy -- this is not
            // accounted for.  It's not immediately clear how to
            // accomplish such a thing.
            for deck_data in &mut decks {
                if self
                    .limits
                    .limit_reached(deck_data.deck_id, LimitKind::New)?
                {
                    deck_data.depleted = true;
                    continue;
                }
                if deck_data.cards.peek().is_none() {
                    deck_data.depleted = true;
                } else {
                    non_depleted_decks += 1;
                }
            }
            let root_limit = self.limits.get_root_limits().get(LimitKind::New);
            let mut sampled_deck_ids = HashSet::<DeckId>::new();
            let sampling = root_limit < non_depleted_decks;
            if sampling {
                let mut deck_ids: Vec<DeckId> = vec![];
                for deck_data in &decks {
                    if !deck_data.depleted {
                        deck_ids.push(deck_data.deck_id);
                    }
                }
                for _i in 0..root_limit {
                    let selected_index = rng.random_range(0..deck_ids.len());
                    let selected_deck_id = deck_ids[selected_index];
                    sampled_deck_ids.insert(selected_deck_id);
                    deck_ids.swap_remove(selected_index);
                }
            }
            for deck_data in &mut decks {
                if self.limits.root_limit_reached(LimitKind::New) {
                    do_continue = false;
                    break;
                }
                if sampling && !sampled_deck_ids.contains(&deck_data.deck_id) {
                    continue;
                }
                if deck_data.depleted {
                    continue;
                }
                if self
                    .limits
                    .limit_reached(deck_data.deck_id, LimitKind::New)?
                {
                    continue;
                }
                if let Some(card) = deck_data.cards.next() {
                    if self.add_new_card(card) {
                        self.limits
                            .decrement_deck_and_parent_limits(deck_data.deck_id, LimitKind::New)?;
                        do_continue = true;
                    }
                }
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
                if self.limits.root_limit_reached(LimitKind::New) {
                    return Ok(false);
                }
                if !self
                    .limits
                    .limit_reached(card.current_deck_id, LimitKind::New)?
                    && self.add_new_card(card)
                {
                    self.limits
                        .decrement_deck_and_parent_limits(card.current_deck_id, LimitKind::New)?;
                }
                Ok(true)
            })
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

    // Generates a salt for use with fnvhash. Useful to increase randomness
    // when the base salt is a small integer.
    fn knuth_salt(base_salt: u32) -> u32 {
        base_salt.wrapping_mul(2654435761)
    }
}
