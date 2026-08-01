// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;

pub use anki_proto::scheduler::schedule_cards_as_new_request::Context as ScheduleAsNewContext;
pub use anki_proto::scheduler::RepositionDefaultsResponse;
pub use anki_proto::scheduler::ScheduleCardsAsNewDefaultsResponse;
use rand::seq::SliceRandom;

use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::BoolKey;
use crate::config::SchedulerVersion;
use crate::deckconfig::NewCardInsertOrder;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::SearchNode;
use crate::search::SortMode;
use crate::search::StateKind;

impl Card {
    pub(crate) fn original_or_current_due(&self) -> i32 {
        if self.is_filtered() {
            self.original_due
        } else {
            self.due
        }
    }

    pub(crate) fn last_position(&self) -> Option<u32> {
        if self.ctype == CardType::New {
            Some(self.original_or_current_due() as u32)
        } else {
            self.original_position
        }
    }

    /// True if the provided position has been used.
    /// (Always true, if restore_position is false.)
    pub(crate) fn schedule_as_new(
        &mut self,
        position: u32,
        reset_counts: bool,
        restore_position: bool,
    ) -> bool {
        let last_position = restore_position.then(|| self.last_position()).flatten();
        self.remove_from_filtered_deck_before_reschedule();
        self.due = last_position.unwrap_or(position) as i32;
        self.ctype = CardType::New;
        self.queue = CardQueue::New;
        self.interval = 0;
        self.ease_factor = 0;
        self.original_position = None;
        if reset_counts {
            self.reps = 0;
            self.lapses = 0;
        }
        self.memory_state = None;

        last_position.is_none()
    }

    /// If the card is new, change its position, and return true.
    fn set_new_position(&mut self, position: u32) -> bool {
        if self.ctype == CardType::New {
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

#[derive(PartialEq, Eq)]
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
                nids.shuffle(&mut rand::rng());
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
            let cards = col.all_cards_for_ids(cids, true)?;
            for mut card in cards {
                let original = card.clone();
                if card.schedule_as_new(position, reset_counts, restore_position) {
                    position += 1;
                }
                if log {
                    col.log_manually_scheduled_review(&card, original.interval, usn)?;
                }
                col.update_card_inner(&mut card, original, usn)?;
            }
            col.set_next_card_position(position)?;

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
            col.set_config_bool_inner(
                BoolKey::RandomOrderReposition,
                order == NewCardDueOrder::Random,
            )?;
            col.set_config_bool_inner(BoolKey::ShiftPositionOfExistingCards, shift)?;
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
        if self.scheduler_version() == SchedulerVersion::V1 {
            return Err(AnkiError::SchedulerUpgradeRequired);
        }
        if shift {
            self.shift_existing_cards(starting_from, step * cids.len() as u32, usn)?;
        }
        let cards = self.all_cards_for_ids(cids, true)?;
        let sorter = NewCardSorter::new(&cards, starting_from, step, order);
        let mut count = 0;
        for mut card in cards {
            let original = card.clone();
            if card.set_new_position(sorter.position(&card)) {
                count += 1;
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(count)
    }

    pub fn reposition_defaults(&self) -> RepositionDefaultsResponse {
        RepositionDefaultsResponse {
            random: self.get_config_bool(BoolKey::RandomOrderReposition),
            shift: self.get_config_bool(BoolKey::ShiftPositionOfExistingCards),
        }
    }

    /// This is handled by update_deck_configs() now; this function has been
    /// kept around for now to support the old deck config screen.
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
            SearchNode::DeckIdsWithoutChildren(deck.to_string()).and(StateKind::New),
            SortMode::NoOrder,
        )?;
        self.sort_cards_inner(&cids, 1, 1, order.into(), false, usn)
    }

    fn shift_existing_cards(&mut self, start: u32, by: u32, usn: Usn) -> Result<()> {
        for mut card in self.storage.all_cards_at_or_above_position(start)? {
            let original = card.clone();
            card.set_new_position(card.due as u32 + by);
            self.update_card_inner(&mut card, original, usn)?;
        }
        Ok(())
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

    #[test]
    fn last_position() {
        // new card
        let mut card = Card::new(NoteId(0), 0, DeckId(1), 42);
        assert_eq!(card.last_position(), Some(42));
        // in filtered deck
        card.original_deck_id.0 = 1;
        card.deck_id.0 = 2;
        card.original_due = 42;
        card.due = 123456789;
        card.queue = CardQueue::Review;
        assert_eq!(card.last_position(), Some(42));

        // graduated card
        let mut card = Card::new(NoteId(0), 0, DeckId(1), 42);
        card.queue = CardQueue::Review;
        card.ctype = CardType::Review;
        card.due = 123456789;
        // only recent clients remember the original position
        assert_eq!(card.last_position(), None);
        card.original_position = Some(42);
        assert_eq!(card.last_position(), Some(42));
    }

    #[test]
    fn scheduling_as_new() {
        let mut card = Card::new(NoteId(0), 0, DeckId(1), 42);
        card.reps = 4;
        card.lapses = 2;
        // keep counts and position
        card.schedule_as_new(1, false, true);
        assert_eq!((card.due, card.reps, card.lapses), (42, 4, 2));
        // complete reset
        card.schedule_as_new(1, true, false);
        assert_eq!((card.due, card.reps, card.lapses), (1, 0, 0));
    }
}
