// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::decks::DeckID;
use crate::define_newtype;
use crate::err::{AnkiError, Result};
use crate::notes::NoteID;
use crate::{collection::RequestContext, timestamp::TimestampSecs, types::Usn};
use num_enum::TryFromPrimitive;
use serde_repr::{Deserialize_repr, Serialize_repr};

define_newtype!(CardID, i64);

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
    UserBuried = -2,
    SchedBuried = -3,
}

#[derive(Debug, Clone)]
pub struct Card {
    pub(crate) id: CardID,
    pub(crate) nid: NoteID,
    pub(crate) did: DeckID,
    pub(crate) ord: u16,
    pub(crate) mtime: TimestampSecs,
    pub(crate) usn: Usn,
    pub(crate) ctype: CardType,
    pub(crate) queue: CardQueue,
    pub(crate) due: i32,
    pub(crate) ivl: u32,
    pub(crate) factor: u16,
    pub(crate) reps: u32,
    pub(crate) lapses: u32,
    pub(crate) left: u32,
    pub(crate) odue: i32,
    pub(crate) odid: DeckID,
    pub(crate) flags: u8,
    pub(crate) data: String,
}

impl Default for Card {
    fn default() -> Self {
        Self {
            id: CardID(0),
            nid: NoteID(0),
            did: DeckID(0),
            ord: 0,
            mtime: TimestampSecs(0),
            usn: Usn(0),
            ctype: CardType::New,
            queue: CardQueue::New,
            due: 0,
            ivl: 0,
            factor: 0,
            reps: 0,
            lapses: 0,
            left: 0,
            odue: 0,
            odid: DeckID(0),
            flags: 0,
            data: "".to_string(),
        }
    }
}

impl RequestContext<'_> {
    pub(crate) fn update_card(&mut self, card: &mut Card) -> Result<()> {
        if card.id.0 == 0 {
            return Err(AnkiError::invalid_input("card id not set"));
        }
        card.mtime = TimestampSecs::now();
        card.usn = self.storage.usn()?;
        self.storage.update_card(card)
    }

    #[allow(dead_code)]
    pub(crate) fn add_card(&mut self, card: &mut Card) -> Result<()> {
        if card.id.0 != 0 {
            return Err(AnkiError::invalid_input("card id already set"));
        }
        card.mtime = TimestampSecs::now();
        card.usn = self.storage.usn()?;
        self.storage.add_card(card)
    }
}
