// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::decks::DeckID;
use crate::define_newtype;
use crate::err::{AnkiError, Result};
use crate::notes::NoteID;
use crate::{
    collection::Collection, config::SchedulerVersion, timestamp::TimestampSecs, types::Usn,
    undo::Undoable,
};
use num_enum::TryFromPrimitive;
use serde_repr::{Deserialize_repr, Serialize_repr};
use std::collections::HashSet;

define_newtype!(CardID, i64);

impl CardID {
    pub fn as_secs(self) -> TimestampSecs {
        TimestampSecs(self.0 / 1000)
    }
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, TryFromPrimitive, Clone, Copy)]
#[repr(u8)]
pub enum CardType {
    New = 0,
    Learn = 1,
    Review = 2,
    Relearn = 3,
}

#[derive(Serialize_repr, Deserialize_repr, Debug, PartialEq, TryFromPrimitive, Clone, Copy)]
#[repr(i8)]
pub enum CardQueue {
    /// due is the order cards are shown in
    New = 0,
    /// due is a unix timestamp
    Learn = 1,
    /// due is days since creation date
    Review = 2,
    DayLearn = 3,
    /// due is a unix timestamp.
    /// preview cards only placed here when failed.
    PreviewRepeat = 4,
    /// cards are not due in these states
    Suspended = -1,
    SchedBuried = -2,
    UserBuried = -3,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Card {
    pub(crate) id: CardID,
    pub(crate) note_id: NoteID,
    pub(crate) deck_id: DeckID,
    pub(crate) template_idx: u16,
    pub(crate) mtime: TimestampSecs,
    pub(crate) usn: Usn,
    pub(crate) ctype: CardType,
    pub(crate) queue: CardQueue,
    pub(crate) due: i32,
    pub(crate) interval: u32,
    pub(crate) ease_factor: u16,
    pub(crate) reps: u32,
    pub(crate) lapses: u32,
    pub(crate) remaining_steps: u32,
    pub(crate) original_due: i32,
    pub(crate) original_deck_id: DeckID,
    pub(crate) flags: u8,
    pub(crate) data: String,
}

impl Default for Card {
    fn default() -> Self {
        Self {
            id: CardID(0),
            note_id: NoteID(0),
            deck_id: DeckID(0),
            template_idx: 0,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            ctype: CardType::New,
            queue: CardQueue::New,
            due: 0,
            interval: 0,
            ease_factor: 0,
            reps: 0,
            lapses: 0,
            remaining_steps: 0,
            original_due: 0,
            original_deck_id: DeckID(0),
            flags: 0,
            data: "".to_string(),
        }
    }
}

impl Card {
    pub fn set_modified(&mut self, usn: Usn) {
        self.mtime = TimestampSecs::now();
        self.usn = usn;
    }

    /// Caller must ensure provided deck exists and is not filtered.
    fn set_deck(&mut self, deck: DeckID, sched: SchedulerVersion) {
        self.remove_from_filtered_deck_restoring_queue(sched);
        self.deck_id = deck;
    }
}
#[derive(Debug)]
pub(crate) struct UpdateCardUndo(Card);

impl Undoable for UpdateCardUndo {
    fn apply(&self, col: &mut crate::collection::Collection, usn: Usn) -> Result<()> {
        let current = col
            .storage
            .get_card(self.0.id)?
            .ok_or_else(|| AnkiError::invalid_input("card disappeared"))?;
        col.update_card(&mut self.0.clone(), &current, usn)
    }
}

impl Card {
    pub fn new(nid: NoteID, ord: u16, deck_id: DeckID, due: i32) -> Self {
        let mut card = Card::default();
        card.note_id = nid;
        card.template_idx = ord;
        card.deck_id = deck_id;
        card.due = due;
        card
    }
}
impl Collection {
    #[cfg(test)]
    pub(crate) fn get_and_update_card<F, T>(&mut self, cid: CardID, func: F) -> Result<Card>
    where
        F: FnOnce(&mut Card) -> Result<T>,
    {
        let orig = self
            .storage
            .get_card(cid)?
            .ok_or_else(|| AnkiError::invalid_input("no such card"))?;
        let mut card = orig.clone();
        func(&mut card)?;
        self.update_card(&mut card, &orig, self.usn()?)?;
        Ok(card)
    }

    pub(crate) fn update_card(&mut self, card: &mut Card, original: &Card, usn: Usn) -> Result<()> {
        if card.id.0 == 0 {
            return Err(AnkiError::invalid_input("card id not set"));
        }
        self.state
            .undo
            .save_undoable(Box::new(UpdateCardUndo(original.clone())));
        card.set_modified(usn);
        self.storage.update_card(card)
    }

    pub(crate) fn add_card(&mut self, card: &mut Card) -> Result<()> {
        if card.id.0 != 0 {
            return Err(AnkiError::invalid_input("card id already set"));
        }
        card.mtime = TimestampSecs::now();
        card.usn = self.usn()?;
        self.storage.add_card(card)
    }

    /// Remove cards and any resulting orphaned notes.
    /// Expects a transaction.
    pub(crate) fn remove_cards_and_orphaned_notes(&mut self, cids: &[CardID]) -> Result<()> {
        let usn = self.usn()?;
        let mut nids = HashSet::new();
        for cid in cids {
            if let Some(card) = self.storage.get_card(*cid)? {
                // fixme: undo
                nids.insert(card.note_id);
                self.storage.remove_card(*cid)?;
                self.storage.add_card_grave(*cid, usn)?;
            }
        }
        for nid in nids {
            if self.storage.note_is_orphaned(nid)? {
                self.remove_note_only(nid, usn)?;
            }
        }

        Ok(())
    }

    pub(crate) fn remove_card_only(&mut self, card: Card, usn: Usn) -> Result<()> {
        // fixme: undo
        self.storage.remove_card(card.id)?;
        self.storage.add_card_grave(card.id, usn)?;

        Ok(())
    }

    pub fn set_deck(&mut self, cards: &[CardID], deck_id: DeckID) -> Result<()> {
        let deck = self.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?;
        if deck.is_filtered() {
            return Err(AnkiError::DeckIsFiltered);
        }
        self.storage.set_search_table_to_card_ids(cards, false)?;
        let sched = self.sched_ver();
        let usn = self.usn()?;
        self.transact(None, |col| {
            for mut card in col.storage.all_searched_cards()? {
                if card.deck_id == deck_id {
                    continue;
                }
                let original = card.clone();
                card.set_deck(deck_id, sched);
                col.update_card(&mut card, &original, usn)?;
            }
            Ok(())
        })
    }
}

#[cfg(test)]
mod test {
    use super::Card;
    use crate::collection::{open_test_collection, CollectionOp};

    #[test]
    fn undo() {
        let mut col = open_test_collection();

        let mut card = Card::default();
        card.interval = 1;
        col.add_card(&mut card).unwrap();
        let cid = card.id;

        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);

        // outside of a transaction, no undo info recorded
        let card = col
            .get_and_update_card(cid, |card| {
                card.interval = 2;
                Ok(())
            })
            .unwrap();
        assert_eq!(card.interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);

        // record a few undo steps
        for i in 3..=4 {
            col.transact(Some(CollectionOp::UpdateCard), |col| {
                col.get_and_update_card(cid, |card| {
                    card.interval = i;
                    Ok(())
                })
                .unwrap();
                Ok(())
            })
            .unwrap();
        }

        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // undo a step
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // and again
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // redo a step
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // and another
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and undo the redo
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // if any action is performed, it should clear the redo queue
        col.transact(Some(CollectionOp::UpdateCard), |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 5;
                Ok(())
            })
            .unwrap();
            Ok(())
        })
        .unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and any action that doesn't support undoing will clear both queues
        col.transact(None, |_col| Ok(())).unwrap();
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);
    }
}
