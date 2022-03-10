// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::{HashMap, HashSet};

use rand::seq::SliceRandom;

pub use crate::backend_proto::scheduler::{
    schedule_cards_as_new_request::Context as ScheduleAsNewContext,
    ScheduleCardsAsNewDefaultsResponse,
};
use crate::{
    card::{CardQueue, CardType},
    config::{BoolKey, SchedulerVersion},
    deckconfig::NewCardInsertOrder,
    prelude::*,
    search::{SearchNode, SortMode, StateKind},
};

impl Card {
    fn schedule_as_new(&mut self, position: u32, reset_counts: bool) {
        self.remove_from_filtered_deck_before_reschedule();
        self.due = position as i32;
        self.ctype = CardType::New;
        self.queue = CardQueue::New;
        self.interval = 0;
        self.ease_factor = 0;
        self.original_position = None;
        if reset_counts {
            self.reps = 0;
            self.lapses = 0;
        }
    }

    /// If the card is new, change its position, and return true.
    fn set_new_position(&mut self, position: u32, v2: bool) -> bool {
        if v2 && self.ctype == CardType::New {
            if self.is_filtered() {
                self.original_due = position as i32;
            } else {
                self.due = position as i32;
            }
            true
        } else if self.queue == CardQueue::New {
            self.due = position as i32;
            true
        } else {
            false
        }
    }
}
pub(crate) struct NewCardSorter {
    position: HashMap<NoteId, u32>,
}

#[derive(PartialEq)]
pub enum NewCardDueOrder {
    NoteId,
    Random,
    Preserve,
}

impl NewCardSorter {
    pub(crate) fn new(
        cards: &[Card],
        starting_from: u32,
        step: u32,
        order: NewCardDueOrder,
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

fn nids_in_desired_order(cards: &[Card], order: NewCardDueOrder) -> Vec<NoteId> {
    if order == NewCardDueOrder::Preserve {
        nids_in_preserved_order(cards)
    } else {
        let nids: HashSet<_> = cards.iter().map(|c| c.note_id).collect();
        let mut nids: Vec<_> = nids.into_iter().collect();
        match order {
            NewCardDueOrder::NoteId => {
                nids.sort_unstable();
            }
            NewCardDueOrder::Random => {
                nids.shuffle(&mut rand::thread_rng());
            }
            NewCardDueOrder::Preserve => unreachable!(),
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
    pub fn reschedule_cards_as_new(
        &mut self,
        cids: &[CardId],
        log: bool,
        restore_position: bool,
        reset_counts: bool,
        context: Option<ScheduleAsNewContext>,
    ) -> Result<OpOutput<()>> {
        let usn = self.usn()?;
        let mut position = self.get_next_card_position();
        self.transact(Op::ScheduleAsNew, |col| {
            col.storage.set_search_table_to_card_ids(cids, true)?;
            let cards = col.storage.all_searched_cards_in_search_order()?;
            for mut card in cards {
                let original = card.clone();
                if restore_position && card.original_position.is_some() {
                    card.schedule_as_new(card.original_position.unwrap(), reset_counts);
                } else {
                    card.schedule_as_new(position, reset_counts);
                    position += 1;
                }
                if log {
                    col.log_manually_scheduled_review(&card, &original, usn)?;
                }
                col.update_card_inner(&mut card, original, usn)?;
            }
            col.set_next_card_position(position)?;
            col.storage.clear_searched_cards_table()?;

            match context {
                Some(ScheduleAsNewContext::Browser) => {
                    col.set_config_bool_inner(BoolKey::RestorePositionBrowser, restore_position)?;
                    col.set_config_bool_inner(BoolKey::ResetCountsBrowser, reset_counts)?;
                }
                Some(ScheduleAsNewContext::Reviewer) => {
                    col.set_config_bool_inner(BoolKey::RestorePositionReviewer, restore_position)?;
                    col.set_config_bool_inner(BoolKey::ResetCountsReviewer, reset_counts)?;
                }
                None => (),
            }

            Ok(())
        })
    }

    pub fn reschedule_cards_as_new_defaults(
        &self,
        context: ScheduleAsNewContext,
    ) -> ScheduleCardsAsNewDefaultsResponse {
        match context {
            ScheduleAsNewContext::Browser => ScheduleCardsAsNewDefaultsResponse {
                restore_position: self.get_config_bool(BoolKey::RestorePositionBrowser),
                reset_counts: self.get_config_bool(BoolKey::ResetCountsBrowser),
            },
            ScheduleAsNewContext::Reviewer => ScheduleCardsAsNewDefaultsResponse {
                restore_position: self.get_config_bool(BoolKey::RestorePositionReviewer),
                reset_counts: self.get_config_bool(BoolKey::ResetCountsReviewer),
            },
        }
    }

    pub fn sort_cards(
        &mut self,
        cids: &[CardId],
        starting_from: u32,
        step: u32,
        order: NewCardDueOrder,
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
        order: NewCardDueOrder,
        shift: bool,
        usn: Usn,
    ) -> Result<usize> {
        let v2 = self.scheduler_version() != SchedulerVersion::V1;
        if shift {
            self.shift_existing_cards(starting_from, step * cids.len() as u32, usn, v2)?;
        }
        self.storage.set_search_table_to_card_ids(cids, true)?;
        let cards = self.storage.all_searched_cards_in_search_order()?;
        let sorter = NewCardSorter::new(&cards, starting_from, step, order);
        let mut count = 0;
        for mut card in cards {
            let original = card.clone();
            if card.set_new_position(sorter.position(&card), v2) {
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
                    NewCardInsertOrder::Random
                } else {
                    NewCardInsertOrder::Due
                },
                col.usn()?,
            )
        })
    }

    pub(crate) fn sort_deck(
        &mut self,
        deck: DeckId,
        order: NewCardInsertOrder,
        usn: Usn,
    ) -> Result<usize> {
        let cids = self.search_cards(
            SearchBuilder::from(SearchNode::DeckIdWithoutChildren(deck)).and(StateKind::New),
            SortMode::NoOrder,
        )?;
        self.sort_cards_inner(&cids, 1, 1, order.into(), false, usn)
    }

    fn shift_existing_cards(&mut self, start: u32, by: u32, usn: Usn, v2: bool) -> Result<()> {
        self.storage.search_cards_at_or_above_position(start)?;
        for mut card in self.storage.all_searched_cards()? {
            let original = card.clone();
            card.set_new_position(card.due as u32 + by, v2);
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
        let sorter = NewCardSorter::new(&cards, 0, 1, NewCardDueOrder::Preserve);
        assert_eq!(sorter.position(&c1), 0);
        assert_eq!(sorter.position(&c2), 1);
        assert_eq!(sorter.position(&c3), 2);

        // NoteId/step/starting
        let sorter = NewCardSorter::new(&cards, 3, 2, NewCardDueOrder::NoteId);
        assert_eq!(sorter.position(&c3), 3);
        assert_eq!(sorter.position(&c2), 5);
        assert_eq!(sorter.position(&c1), 7);

        // Random
        let mut c1_positions = HashSet::new();
        for _ in 1..100 {
            let sorter = NewCardSorter::new(&cards, 0, 1, NewCardDueOrder::Random);
            c1_positions.insert(sorter.position(&c1));
            if c1_positions.len() == cards.len() {
                return;
            }
        }
        unreachable!("not random");
    }
}

impl From<NewCardInsertOrder> for NewCardDueOrder {
    fn from(o: NewCardInsertOrder) -> Self {
        match o {
            NewCardInsertOrder::Due => NewCardDueOrder::NoteId,
            NewCardInsertOrder::Random => NewCardDueOrder::Random,
        }
    }
}
