// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{card::CardID, decks::DeckID, err::Result, notes::NoteID, types::Usn};
use rusqlite::{params, NO_PARAMS};

enum GraveKind {
    Card,
    Note,
    Deck,
}

impl SqliteStorage {
    fn add_grave(&self, oid: i64, kind: GraveKind, usn: Usn) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![usn, oid, kind as u8])?;
        Ok(())
    }

    pub(crate) fn clear_all_graves(&self) -> Result<()> {
        self.db.execute("delete from graves", NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn add_card_grave(&self, cid: CardID, usn: Usn) -> Result<()> {
        self.add_grave(cid.0, GraveKind::Card, usn)
    }

    pub(crate) fn add_note_grave(&self, nid: NoteID, usn: Usn) -> Result<()> {
        self.add_grave(nid.0, GraveKind::Note, usn)
    }

    pub(crate) fn add_deck_grave(&self, did: DeckID, usn: Usn) -> Result<()> {
        self.add_grave(did.0, GraveKind::Deck, usn)
    }
}
