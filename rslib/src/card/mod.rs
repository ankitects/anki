// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod undo;

use std::collections::HashSet;

use num_enum::TryFromPrimitive;
use serde_repr::{Deserialize_repr, Serialize_repr};

use crate::{
    collection::Collection,
    config::SchedulerVersion,
    deckconfig::DeckConfig,
    decks::DeckId,
    define_newtype,
    error::{AnkiError, FilteredDeckError, Result},
    notes::NoteId,
    ops::StateChanges,
    prelude::*,
    timestamp::TimestampSecs,
    types::Usn,
};

define_newtype!(CardId, i64);

impl CardId {
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
    pub(crate) id: CardId,
    pub(crate) note_id: NoteId,
    pub(crate) deck_id: DeckId,
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
    pub(crate) original_deck_id: DeckId,
    pub(crate) flags: u8,
    pub(crate) data: String,
}

impl Default for Card {
    fn default() -> Self {
        Self {
            id: CardId(0),
            note_id: NoteId(0),
            deck_id: DeckId(0),
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
            original_deck_id: DeckId(0),
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
    fn set_deck(&mut self, deck: DeckId, sched: SchedulerVersion) {
        self.remove_from_filtered_deck_restoring_queue(sched);
        self.deck_id = deck;
    }

    /// True if flag changed.
    fn set_flag(&mut self, flag: u8) -> bool {
        // The first 3 bits represent one of the 7 supported flags, the rest of
        // the flag byte is preserved.
        let updated_flags = (self.flags & !0b111) | flag;
        if self.flags != updated_flags {
            self.flags = updated_flags;
            true
        } else {
            false
        }
    }

    /// Return the total number of steps left to do, ignoring the
    /// "steps today" number packed into the DB representation.
    pub fn remaining_steps(&self) -> u32 {
        self.remaining_steps % 1000
    }

    /// Return ease factor as a multiplier (eg 2.5)
    pub fn ease_factor(&self) -> f32 {
        (self.ease_factor as f32) / 1000.0
    }

    pub fn is_intraday_learning(&self) -> bool {
        matches!(self.queue, CardQueue::Learn | CardQueue::PreviewRepeat)
    }
}

impl Card {
    pub fn new(note_id: NoteId, template_idx: u16, deck_id: DeckId, due: i32) -> Self {
        Card {
            note_id,
            deck_id,
            template_idx,
            due,
            ..Default::default()
        }
    }
}

impl Collection {
    pub(crate) fn update_cards_maybe_undoable(
        &mut self,
        cards: Vec<Card>,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        if undoable {
            self.transact(Op::UpdateCard, |col| {
                for mut card in cards {
                    let existing = col.storage.get_card(card.id)?.ok_or(AnkiError::NotFound)?;
                    col.update_card_inner(&mut card, existing, col.usn()?)?
                }
                Ok(())
            })
        } else {
            self.transact_no_undo(|col| {
                for mut card in cards {
                    let existing = col.storage.get_card(card.id)?.ok_or(AnkiError::NotFound)?;
                    col.update_card_inner(&mut card, existing, col.usn()?)?;
                }
                Ok(OpOutput {
                    output: (),
                    changes: OpChanges {
                        op: Op::UpdateCard,
                        changes: StateChanges {
                            card: true,
                            ..Default::default()
                        },
                    },
                })
            })
        }
    }

    #[cfg(test)]
    pub(crate) fn get_and_update_card<F, T>(&mut self, cid: CardId, func: F) -> Result<Card>
    where
        F: FnOnce(&mut Card) -> Result<T>,
    {
        let orig = self
            .storage
            .get_card(cid)?
            .ok_or_else(|| AnkiError::invalid_input("no such card"))?;
        let mut card = orig.clone();
        func(&mut card)?;
        self.update_card_inner(&mut card, orig, self.usn()?)?;
        Ok(card)
    }

    /// Marks the card as modified, then saves it.
    pub(crate) fn update_card_inner(
        &mut self,
        card: &mut Card,
        original: Card,
        usn: Usn,
    ) -> Result<()> {
        card.set_modified(usn);
        self.update_card_undoable(card, original)
    }

    pub(crate) fn add_card(&mut self, card: &mut Card) -> Result<()> {
        if card.id.0 != 0 {
            return Err(AnkiError::invalid_input("card id already set"));
        }
        card.mtime = TimestampSecs::now();
        card.usn = self.usn()?;
        self.add_card_undoable(card)
    }

    /// Remove cards and any resulting orphaned notes.
    /// Expects a transaction.
    pub(crate) fn remove_cards_and_orphaned_notes(&mut self, cids: &[CardId]) -> Result<()> {
        let usn = self.usn()?;
        let mut nids = HashSet::new();
        for cid in cids {
            if let Some(card) = self.storage.get_card(*cid)? {
                nids.insert(card.note_id);
                self.remove_card_and_add_grave_undoable(card, usn)?;
            }
        }
        for nid in nids {
            if self.storage.note_is_orphaned(nid)? {
                self.remove_note_only_undoable(nid, usn)?;
            }
        }

        Ok(())
    }

    pub fn set_deck(&mut self, cards: &[CardId], deck_id: DeckId) -> Result<OpOutput<usize>> {
        let deck = self.get_deck(deck_id)?.ok_or(AnkiError::NotFound)?;
        if deck.is_filtered() {
            return Err(FilteredDeckError::CanNotMoveCardsInto.into());
        }
        self.storage.set_search_table_to_card_ids(cards, false)?;
        let sched = self.scheduler_version();
        let usn = self.usn()?;
        self.transact(Op::SetCardDeck, |col| {
            let mut count = 0;
            for mut card in col.storage.all_searched_cards()? {
                if card.deck_id == deck_id {
                    continue;
                }
                count += 1;
                let original = card.clone();
                card.set_deck(deck_id, sched);
                col.update_card_inner(&mut card, original, usn)?;
            }
            Ok(count)
        })
    }

    pub fn set_card_flag(&mut self, cards: &[CardId], flag: u32) -> Result<OpOutput<usize>> {
        if flag > 7 {
            return Err(AnkiError::invalid_input("invalid flag"));
        }
        let flag = flag as u8;

        self.storage.set_search_table_to_card_ids(cards, false)?;
        let usn = self.usn()?;
        self.transact(Op::SetFlag, |col| {
            let mut count = 0;
            for mut card in col.storage.all_searched_cards()? {
                let original = card.clone();
                if card.set_flag(flag) {
                    col.update_card_inner(&mut card, original, usn)?;
                    count += 1;
                }
            }
            Ok(count)
        })
    }

    /// Get deck config for the given card. If missing, return default values.
    #[allow(dead_code)]
    pub(crate) fn deck_config_for_card(&mut self, card: &Card) -> Result<DeckConfig> {
        if let Some(deck) = self.get_deck(card.original_or_current_deck_id())? {
            if let Some(conf_id) = deck.config_id() {
                return Ok(self.get_deck_config(conf_id, true)?.unwrap());
            }
        }

        Ok(DeckConfig::default())
    }
}
