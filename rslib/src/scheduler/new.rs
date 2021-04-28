// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::{HashMap, HashSet};

use rand::seq::SliceRandom;

use crate::{
    card::{CardQueue, CardType},
    deckconfig::NewCardOrder,
    prelude::*,
    search::SortMode,
};

impl Card {
    fn schedule_as_new(&mut self, position: u32) {
        self.remove_from_filtered_deck_before_reschedule();
        self.due = position as i32;
        self.ctype = CardType::New;
        self.queue = CardQueue::New;
        self.interval = 0;
        self.ease_factor = 0;
    }

    /// If the card is new, change its position, and return true.
    fn set_new_position(&mut self, position: u32) -> bool {
        if self.queue != CardQueue::New || self.ctype != CardType::New {
            false
        } else {
            self.due = position as i32;
            true
        }
    }
}
pub(crate) struct NewCardSorter {
    position: HashMap<NoteId, u32>,
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

fn nids_in_desired_order(cards: &[Card], order: NewCardSortOrder) -> Vec<NoteId> {
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

fn nids_in_preserved_order(cards: &[Card]) -> Vec<NoteId> {
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
    pub fn reschedule_cards_as_new(&mut self, cids: &[CardId], log: bool) -> Result<OpOutput<()>> {
        let usn = self.usn()?;
        let mut position = self.get_next_card_position();
        self.transact(Op::ScheduleAsNew, |col| {
            col.storage.set_search_table_to_card_ids(cids, true)?;
            let cards = col.storage.all_searched_cards_in_search_order()?;
            for mut card in cards {
                let original = card.clone();
                card.schedule_as_new(position);
                if log {
                    col.log_manually_scheduled_review(&card, &original, usn)?;
                }
                col.update_card_inner(&mut card, original, usn)?;
                position += 1;
            }
            col.set_next_card_position(position)?;
            col.storage.clear_searched_cards_table()
        })
    }

    pub fn sort_cards(
        &mut self,
        cids: &[CardId],
        starting_from: u32,
        step: u32,
        order: NewCardSortOrder,
        shift: bool,
    ) -> Result<OpOutput<usize>> {
        let usn = self.usn()?;
        self.transact(Op::SortCards, |col| {
            col.sort_cards_inner(cids, starting_from, step, order, shift, usn)
        })
    }

    fn sort_cards_inner(
        &mut self,
        cids: &[CardId],
        starting_from: u32,
        step: u32,
        order: NewCardSortOrder,
        shift: bool,
        usn: Usn,
    ) -> Result<usize> {
        if shift {
            self.shift_existing_cards(starting_from, step * cids.len() as u32, usn)?;
        }
        self.storage.set_search_table_to_card_ids(cids, true)?;
        let cards = self.storage.all_searched_cards_in_search_order()?;
        let sorter = NewCardSorter::new(&cards, starting_from, step, order);
        let mut count = 0;
        for mut card in cards {
            let original = card.clone();
            if card.set_new_position(sorter.position(&card)) {
                count += 1;
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        self.storage.clear_searched_cards_table()?;
        Ok(count)
    }

    /// This is handled by update_deck_configs() now; this function has been kept around
    /// for now to support the old deck config screen.
    pub fn sort_deck_legacy(&mut self, deck: DeckId, random: bool) -> Result<OpOutput<usize>> {
        self.transact(Op::SortCards, |col| {
            col.sort_deck(
                deck,
                if random {
                    NewCardOrder::Random
                } else {
                    NewCardOrder::Due
                },
                col.usn()?,
            )
        })
    }

    pub(crate) fn sort_deck(
        &mut self,
        deck: DeckId,
        order: NewCardOrder,
        usn: Usn,
    ) -> Result<usize> {
        let cids = self.search_cards(&format!("did:{} is:new", deck), SortMode::NoOrder)?;
        self.sort_cards_inner(&cids, 1, 1, order.into(), false, usn)
    }

    fn shift_existing_cards(&mut self, start: u32, by: u32, usn: Usn) -> Result<()> {
        self.storage.search_cards_at_or_above_position(start)?;
        for mut card in self.storage.all_searched_cards()? {
            let original = card.clone();
            card.set_new_position(card.due as u32 + by);
            self.update_card_inner(&mut card, original, usn)?;
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
        let mut c1 = Card::new(NoteId(6), 0, DeckId(0), 0);
        c1.id.0 = 2;
        let mut c2 = Card::new(NoteId(5), 0, DeckId(0), 0);
        c2.id.0 = 3;
        let mut c3 = Card::new(NoteId(4), 0, DeckId(0), 0);
        c3.id.0 = 1;
        let cards = vec![c1.clone(), c2.clone(), c3.clone()];

        // Preserve
        let sorter = NewCardSorter::new(&cards, 0, 1, NewCardSortOrder::Preserve);
        assert_eq!(sorter.position(&c1), 0);
        assert_eq!(sorter.position(&c2), 1);
        assert_eq!(sorter.position(&c3), 2);

        // NoteId/step/starting
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

impl From<NewCardOrder> for NewCardSortOrder {
    fn from(o: NewCardOrder) -> Self {
        match o {
            NewCardOrder::Due => NewCardSortOrder::NoteId,
            NewCardOrder::Random => NewCardSortOrder::Random,
        }
    }
}
