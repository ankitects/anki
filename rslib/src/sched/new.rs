// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
    deckconf::INITIAL_EASE_FACTOR_THOUSANDS,
    decks::DeckID,
    err::Result,
    notes::NoteID,
    search::SortMode,
    types::Usn,
};
use rand::seq::SliceRandom;
use std::collections::{HashMap, HashSet};

impl Card {
    fn schedule_as_new(&mut self, position: u32) {
        self.remove_from_filtered_deck_before_reschedule();
        self.due = position as i32;
        self.ctype = CardType::New;
        self.queue = CardQueue::New;
        self.interval = 0;
        if self.ease_factor == 0 {
            // unlike the old Python code, we leave the ease factor alone
            // if it's already set
            self.ease_factor = INITIAL_EASE_FACTOR_THOUSANDS;
        }
    }

    /// If the card is new, change its position.
    fn set_new_position(&mut self, position: u32) {
        if self.queue != CardQueue::New || self.ctype != CardType::New {
            return;
        }
        self.due = position as i32;
    }
}
pub(crate) struct NewCardSorter {
    position: HashMap<NoteID, u32>,
}

impl NewCardSorter {
    pub(crate) fn new(cards: &[Card], starting_from: u32, step: u32, random: bool) -> Self {
        let nids: HashSet<_> = cards.iter().map(|c| c.note_id).collect();
        let mut nids: Vec<_> = nids.into_iter().collect();
        if random {
            nids.shuffle(&mut rand::thread_rng());
        } else {
            nids.sort_unstable();
        }

        NewCardSorter {
            position: nids
                .into_iter()
                .enumerate()
                .map(|(i, nid)| (nid, ((i as u32) * step) + starting_from))
                .collect(),
        }
    }

    pub(crate) fn position(&self, card: &Card) -> u32 {
        self.position
            .get(&card.note_id)
            .cloned()
            .unwrap_or_default()
    }
}

impl Collection {
    pub fn reschedule_cards_as_new(&mut self, cids: &[CardID]) -> Result<()> {
        let usn = self.usn()?;
        let mut position = self.get_next_card_position();
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids)?;
            let cards = col.storage.all_searched_cards()?;
            for mut card in cards {
                let original = card.clone();
                col.log_manually_scheduled_review(&card, usn, 0)?;
                card.schedule_as_new(position);
                col.update_card(&mut card, &original, usn)?;
                position += 1;
            }
            col.set_next_card_position(position)?;
            col.storage.clear_searched_cards_table()?;
            Ok(())
        })
    }

    pub fn sort_cards(
        &mut self,
        cids: &[CardID],
        starting_from: u32,
        step: u32,
        random: bool,
        shift: bool,
    ) -> Result<()> {
        let usn = self.usn()?;
        self.transact(None, |col| {
            col.sort_cards_inner(cids, starting_from, step, random, shift, usn)
        })
    }

    fn sort_cards_inner(
        &mut self,
        cids: &[CardID],
        starting_from: u32,
        step: u32,
        random: bool,
        shift: bool,
        usn: Usn,
    ) -> Result<()> {
        if shift {
            self.shift_existing_cards(starting_from, step * cids.len() as u32, usn)?;
        }
        self.storage.set_search_table_to_card_ids(cids)?;
        let cards = self.storage.all_searched_cards()?;
        let sorter = NewCardSorter::new(&cards, starting_from, step, random);
        for mut card in cards {
            let original = card.clone();
            card.set_new_position(sorter.position(&card));
            self.update_card(&mut card, &original, usn)?;
        }
        self.storage.clear_searched_cards_table()
    }

    /// This creates a transaction - we probably want to split it out
    /// in the future if calling it as part of a deck options update.
    pub fn sort_deck(&mut self, deck: DeckID, random: bool) -> Result<()> {
        let cids = self.search_cards(&format!("did:{}", deck), SortMode::NoOrder)?;
        self.sort_cards(&cids, 1, 1, random, false)
    }

    fn shift_existing_cards(&mut self, start: u32, by: u32, usn: Usn) -> Result<()> {
        self.storage.search_cards_at_or_above_position(start)?;
        for mut card in self.storage.all_searched_cards()? {
            let original = card.clone();
            card.set_new_position(card.due as u32 + by);
            self.update_card(&mut card, &original, usn)?;
        }
        Ok(())
    }
}
