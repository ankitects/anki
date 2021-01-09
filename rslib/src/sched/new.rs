// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    collection::Collection,
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
        self.ease_factor = 0;
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

#[derive(PartialEq)]
pub enum NewCardSortOrder {
    NoteId,
    Random,
    Preserve,
}

impl NewCardSorter {
    pub(crate) fn new(
        cards: &[Card],
        starting_from: u32,
        step: u32,
        order: NewCardSortOrder,
    ) -> Self {
        let nids = nids_in_desired_order(cards, order);

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

fn nids_in_desired_order(cards: &[Card], order: NewCardSortOrder) -> Vec<NoteID> {
    if order == NewCardSortOrder::Preserve {
        nids_in_preserved_order(cards)
    } else {
        let nids: HashSet<_> = cards.iter().map(|c| c.note_id).collect();
        let mut nids: Vec<_> = nids.into_iter().collect();
        match order {
            NewCardSortOrder::NoteId => {
                nids.sort_unstable();
            }
            NewCardSortOrder::Random => {
                nids.shuffle(&mut rand::thread_rng());
            }
            NewCardSortOrder::Preserve => unreachable!(),
        }
        nids
    }
}

fn nids_in_preserved_order(cards: &[Card]) -> Vec<NoteID> {
    let mut seen = HashSet::new();
    cards
        .iter()
        .filter_map(|card| {
            if seen.insert(card.note_id) {
                Some(card.note_id)
            } else {
                None
            }
        })
        .collect()
}

impl Collection {
    pub fn reschedule_cards_as_new(&mut self, cids: &[CardID], log: bool) -> Result<()> {
        let usn = self.usn()?;
        let mut position = self.get_next_card_position();
        self.transact(None, |col| {
            col.storage.set_search_table_to_card_ids(cids, true)?;
            let cards = col.storage.all_searched_cards_in_search_order()?;
            for mut card in cards {
                let original = card.clone();
                if log {
                    col.log_manually_scheduled_review(&card, usn, 0)?;
                }
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
        order: NewCardSortOrder,
        shift: bool,
    ) -> Result<()> {
        let usn = self.usn()?;
        self.transact(None, |col| {
            col.sort_cards_inner(cids, starting_from, step, order, shift, usn)
        })
    }

    fn sort_cards_inner(
        &mut self,
        cids: &[CardID],
        starting_from: u32,
        step: u32,
        order: NewCardSortOrder,
        shift: bool,
        usn: Usn,
    ) -> Result<()> {
        if shift {
            self.shift_existing_cards(starting_from, step * cids.len() as u32, usn)?;
        }
        self.storage.set_search_table_to_card_ids(cids, true)?;
        let cards = self.storage.all_searched_cards_in_search_order()?;
        let sorter = NewCardSorter::new(&cards, starting_from, step, order);
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
        let order = if random {
            NewCardSortOrder::Random
        } else {
            NewCardSortOrder::NoteId
        };
        self.sort_cards(&cids, 1, 1, order, false)
    }

    fn shift_existing_cards(&mut self, start: u32, by: u32, usn: Usn) -> Result<()> {
        self.storage.search_cards_at_or_above_position(start)?;
        for mut card in self.storage.all_searched_cards()? {
            let original = card.clone();
            card.set_new_position(card.due as u32 + by);
            self.update_card(&mut card, &original, usn)?;
        }
        self.storage.clear_searched_cards_table()?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn new_order() {
        let mut c1 = Card::new(NoteID(6), 0, DeckID(0), 0);
        c1.id.0 = 2;
        let mut c2 = Card::new(NoteID(5), 0, DeckID(0), 0);
        c2.id.0 = 3;
        let mut c3 = Card::new(NoteID(4), 0, DeckID(0), 0);
        c3.id.0 = 1;
        let cards = vec![c1.clone(), c2.clone(), c3.clone()];

        // Preserve
        let sorter = NewCardSorter::new(&cards, 0, 1, NewCardSortOrder::Preserve);
        assert_eq!(sorter.position(&c1), 0);
        assert_eq!(sorter.position(&c2), 1);
        assert_eq!(sorter.position(&c3), 2);

        // NoteID/step/starting
        let sorter = NewCardSorter::new(&cards, 3, 2, NewCardSortOrder::NoteId);
        assert_eq!(sorter.position(&c3), 3);
        assert_eq!(sorter.position(&c2), 5);
        assert_eq!(sorter.position(&c1), 7);

        // Random
        let mut c1_positions = HashSet::new();
        for _ in 1..100 {
            let sorter = NewCardSorter::new(&cards, 0, 1, NewCardSortOrder::Random);
            c1_positions.insert(sorter.position(&c1));
            if c1_positions.len() == cards.len() {
                return;
            }
        }
        unreachable!("not random");
    }
}
