// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    card::CardID,
    decks::DeckID,
    err::{AnkiError, Result},
    notes::NoteID,
    sync::Graves,
    types::Usn,
};
use num_enum::TryFromPrimitive;
use rusqlite::{params, NO_PARAMS};
use std::convert::TryFrom;

#[derive(TryFromPrimitive)]
#[repr(u8)]
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

    pub(crate) fn pending_graves(&self, pending_usn: Usn) -> Result<Graves> {
        let mut stmt = self.db.prepare(&format!(
            "select oid, type from graves where {}",
            pending_usn.pending_object_clause()
        ))?;
        let mut rows = stmt.query(&[pending_usn])?;
        let mut graves = Graves::default();
        while let Some(row) = rows.next()? {
            let oid: i64 = row.get(0)?;
            let kind = GraveKind::try_from(row.get::<_, u8>(1)?)
                .map_err(|_| AnkiError::invalid_input("invalid grave kind"))?;
            match kind {
                GraveKind::Card => graves.cards.push(CardID(oid)),
                GraveKind::Note => graves.notes.push(NoteID(oid)),
                GraveKind::Deck => graves.decks.push(DeckID(oid)),
            }
        }
        Ok(graves)
    }

    // fixme: graves is missing an index
    pub(crate) fn update_pending_grave_usns(&self, new_usn: Usn) -> Result<()> {
        self.db
            .prepare("update graves set usn=? where usn=-1")?
            .execute(&[new_usn])?;
        Ok(())
    }
}
