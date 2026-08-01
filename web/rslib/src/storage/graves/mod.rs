// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use num_enum::TryFromPrimitive;
use rusqlite::params;

use super::SqliteStorage;
use crate::prelude::*;
use crate::sync::collection::graves::Graves;

#[derive(TryFromPrimitive)]
#[repr(u8)]
enum GraveKind {
    Card,
    Note,
    Deck,
}

impl SqliteStorage {
    pub(crate) fn clear_all_graves(&self) -> Result<()> {
        self.db.execute("delete from graves", [])?;
        Ok(())
    }

    pub(crate) fn add_card_grave(&self, cid: CardId, usn: Usn) -> Result<()> {
        self.add_grave(cid.0, GraveKind::Card, usn)
    }

    pub(crate) fn add_note_grave(&self, nid: NoteId, usn: Usn) -> Result<()> {
        self.add_grave(nid.0, GraveKind::Note, usn)
    }

    pub(crate) fn add_deck_grave(&self, did: DeckId, usn: Usn) -> Result<()> {
        self.add_grave(did.0, GraveKind::Deck, usn)
    }

    pub(crate) fn remove_card_grave(&self, cid: CardId) -> Result<()> {
        self.remove_grave(cid.0, GraveKind::Card)
    }

    pub(crate) fn remove_note_grave(&self, nid: NoteId) -> Result<()> {
        self.remove_grave(nid.0, GraveKind::Note)
    }

    pub(crate) fn remove_deck_grave(&self, did: DeckId) -> Result<()> {
        self.remove_grave(did.0, GraveKind::Deck)
    }

    pub(crate) fn pending_graves(&self, pending_usn: Usn) -> Result<Graves> {
        let mut stmt = self.db.prepare(&format!(
            "select oid, type from graves where {}",
            pending_usn.pending_object_clause()
        ))?;
        let mut rows = stmt.query([pending_usn])?;
        let mut graves = Graves::default();
        while let Some(row) = rows.next()? {
            let oid: i64 = row.get(0)?;
            let kind =
                GraveKind::try_from(row.get::<_, u8>(1)?).or_invalid("invalid grave kind")?;
            match kind {
                GraveKind::Card => graves.cards.push(CardId(oid)),
                GraveKind::Note => graves.notes.push(NoteId(oid)),
                GraveKind::Deck => graves.decks.push(DeckId(oid)),
            }
        }
        Ok(graves)
    }

    pub(crate) fn update_pending_grave_usns(&self, new_usn: Usn) -> Result<()> {
        self.db
            .prepare("update graves set usn=? where usn=-1")?
            .execute([new_usn])?;
        Ok(())
    }

    fn add_grave(&self, oid: i64, kind: GraveKind, usn: Usn) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![usn, oid, kind as u8])?;
        Ok(())
    }

    /// Only useful when undoing
    fn remove_grave(&self, oid: i64, kind: GraveKind) -> Result<()> {
        self.db
            .prepare_cached(include_str!("remove.sql"))?
            .execute(params![oid, kind as u8])?;
        Ok(())
    }
}
