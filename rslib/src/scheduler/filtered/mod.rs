// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod card;
mod custom_study;

use crate::config::ConfigKey;
use crate::config::SchedulerVersion;
use crate::decks::FilteredDeck;
use crate::decks::FilteredSearchTerm;
use crate::error::FilteredDeckError;
use crate::prelude::*;
use crate::scheduler::timing::SchedTimingToday;
use crate::search::writer::deck_search;
use crate::search::writer::normalize_search;
use crate::search::SortMode;
use crate::storage::card::filtered::order_and_limit_for_search;

/// Contains the parts of a filtered deck required for modifying its settings in
/// the UI.
pub struct FilteredDeckForUpdate {
    pub id: DeckId,
    pub human_name: String,
    pub config: FilteredDeck,
    pub allow_empty: bool,
}

pub(crate) struct DeckFilterContext<'a> {
    pub target_deck: DeckId,
    pub config: &'a FilteredDeck,
    pub usn: Usn,
    pub timing: SchedTimingToday,
}

impl Collection {
    /// Get an existing filtered deck, or create a new one if `deck_id` is 0.
    /// The new deck will not be added to the DB.
    pub fn get_or_create_filtered_deck(
        &mut self,
        deck_id: DeckId,
    ) -> Result<FilteredDeckForUpdate> {
        let deck = if deck_id.0 == 0 {
            self.new_filtered_deck_for_adding()?
        } else {
            self.storage.get_deck(deck_id)?.or_not_found(deck_id)?
        };

        deck.try_into()
    }

    /// If the provided `deck_id` is 0, add provided deck to the DB, and rebuild
    /// it. If the searches are invalid or do not match anything, adding is
    /// aborted. If an existing deck is provided, it will be updated.
    /// Invalid searches or an empty match will abort the update.
    /// Returns the deck_id, which will have changed if the id was 0.
    pub fn add_or_update_filtered_deck(
        &mut self,
        deck: FilteredDeckForUpdate,
    ) -> Result<OpOutput<DeckId>> {
        self.transact(Op::BuildFilteredDeck, |col| {
            col.add_or_update_filtered_deck_inner(deck)
        })
    }

    pub fn empty_filtered_deck(&mut self, did: DeckId) -> Result<OpOutput<()>> {
        self.transact(Op::EmptyFilteredDeck, |col| {
            col.return_all_cards_in_filtered_deck(did)
        })
    }

    // Unlike the old Python code, this also marks the cards as modified.
    pub fn rebuild_filtered_deck(&mut self, did: DeckId) -> Result<OpOutput<usize>> {
        self.transact(Op::RebuildFilteredDeck, |col| {
            let deck = col.get_deck(did)?.or_not_found(did)?;
            col.rebuild_filtered_deck_inner(&deck, col.usn()?)
        })
    }
}

impl Collection {
    pub(crate) fn return_all_cards_in_filtered_deck(&mut self, did: DeckId) -> Result<()> {
        let cids = self.storage.all_cards_in_single_deck(did)?;
        self.return_cards_to_home_deck(&cids)
    }

    // Unlike the old Python code, this also marks the cards as modified.
    fn return_cards_to_home_deck(&mut self, cids: &[CardId]) -> Result<()> {
        let usn = self.usn()?;
        for cid in cids {
            if let Some(mut card) = self.storage.get_card(*cid)? {
                let original = card.clone();
                card.remove_from_filtered_deck_restoring_queue();
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }

    fn build_filtered_deck(&mut self, ctx: DeckFilterContext) -> Result<usize> {
        let start = -100_000;
        let mut position = start;
        let fsrs = self.get_config_bool(BoolKey::Fsrs);
        for term in ctx.config.search_terms.iter().take(2) {
            position = self.move_cards_matching_term(&ctx, term, position, fsrs)?;
        }

        Ok((position - start) as usize)
    }

    /// Move matching cards into filtered deck.
    /// Returns the new starting position.
    fn move_cards_matching_term(
        &mut self,
        ctx: &DeckFilterContext,
        term: &FilteredSearchTerm,
        mut position: i32,
        fsrs: bool,
    ) -> Result<i32> {
        let search = format!(
            "{} -is:suspended -is:buried -deck:filtered",
            if term.search.trim().is_empty() {
                "".to_string()
            } else {
                format!("({})", term.search)
            }
        );
        let order = order_and_limit_for_search(term, ctx.timing, fsrs);

        for mut card in self.all_cards_for_search_in_order(&search, SortMode::Custom(order))? {
            let original = card.clone();
            card.move_into_filtered_deck(ctx, position);
            self.update_card_inner(&mut card, original, ctx.usn)?;
            position += 1;
        }

        Ok(position)
    }

    fn get_next_filtered_deck_name(&self) -> NativeDeckName {
        NativeDeckName::from_native_str(format!(
            "Filtered Deck {}",
            TimestampSecs::now().time_string()
        ))
    }

    fn add_or_update_filtered_deck_inner(
        &mut self,
        mut update: FilteredDeckForUpdate,
    ) -> Result<DeckId> {
        let usn = self.usn()?;
        let allow_empty = update.allow_empty;

        // check the searches are valid, and normalize them
        for term in &mut update.config.search_terms {
            term.search = normalize_search(&term.search)?
        }

        // add or update the deck
        let mut deck: Deck;
        if update.id.0 == 0 {
            deck = Deck::new_filtered();
            apply_update_to_filtered_deck(&mut deck, update);
            self.add_deck_inner(&mut deck, usn)?;
        } else {
            let original = self.storage.get_deck(update.id)?.or_not_found(update.id)?;
            deck = original.clone();
            apply_update_to_filtered_deck(&mut deck, update);
            self.update_deck_inner(&mut deck, original, usn)?;
        }

        // rebuild it
        let count = self.rebuild_filtered_deck_inner(&deck, usn)?;

        // if it failed to match any cards, we revert the changes
        if count == 0 && !allow_empty {
            Err(FilteredDeckError::SearchReturnedNoCards.into())
        } else {
            // update current deck and return id
            self.set_config(ConfigKey::CurrentDeckId, &deck.id)?;
            Ok(deck.id)
        }
    }

    fn rebuild_filtered_deck_inner(&mut self, deck: &Deck, usn: Usn) -> Result<usize> {
        if self.scheduler_version() == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }

        let config = deck.filtered()?;
        let timing = self.timing_today()?;
        let ctx = DeckFilterContext {
            target_deck: deck.id,
            config,
            usn,
            timing,
        };

        self.return_all_cards_in_filtered_deck(deck.id)?;
        self.build_filtered_deck(ctx)
    }

    fn new_filtered_deck_for_adding(&mut self) -> Result<Deck> {
        let mut deck = Deck {
            name: self.get_next_filtered_deck_name(),
            ..Deck::new_filtered()
        };
        if let Some(current) = self.get_deck(self.get_current_deck_id())? {
            if !current.is_filtered() && current.id.0 != 0 {
                // start with a search based on the selected deck name
                let search = deck_search(&current.human_name());
                let term1 = deck
                    .filtered_mut()
                    .unwrap()
                    .search_terms
                    .get_mut(0)
                    .unwrap();
                term1.search = format!("{} is:due", search);
                let term2 = deck
                    .filtered_mut()
                    .unwrap()
                    .search_terms
                    .get_mut(1)
                    .unwrap();
                term2.search = format!("{} is:new", search);
            }
        }

        Ok(deck)
    }
}

impl TryFrom<Deck> for FilteredDeckForUpdate {
    type Error = AnkiError;

    fn try_from(value: Deck) -> Result<Self, Self::Error> {
        let human_name = value.human_name();
        match value.kind {
            DeckKind::Filtered(filtered) => Ok(FilteredDeckForUpdate {
                id: value.id,
                human_name,
                config: filtered,
                allow_empty: false,
            }),
            _ => invalid_input!("not filtered"),
        }
    }
}

fn apply_update_to_filtered_deck(deck: &mut Deck, update: FilteredDeckForUpdate) {
    deck.id = update.id;
    deck.name = NativeDeckName::from_human_name(&update.human_name);
    deck.kind = DeckKind::Filtered(update.config);
}
