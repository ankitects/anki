// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    decks::DeckID,
    err::Result,
    notes::NoteID,
    timestamp::{TimestampMillis, TimestampSecs},
    types::Usn,
};
use rusqlite::params;
use rusqlite::{
    types::{FromSql, FromSqlError, ValueRef},
    OptionalExtension, Row, NO_PARAMS,
};
use std::{convert::TryFrom, result};

impl FromSql for CardType {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

impl FromSql for CardQueue {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as i8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_card(row: &Row) -> result::Result<Card, rusqlite::Error> {
    Ok(Card {
        id: row.get(0)?,
        nid: row.get(1)?,
        did: row.get(2)?,
        ord: row.get(3)?,
        mtime: row.get(4)?,
        usn: row.get(5)?,
        ctype: row.get(6)?,
        queue: row.get(7)?,
        due: row.get(8).ok().unwrap_or_default(),
        ivl: row.get(9)?,
        factor: row.get(10)?,
        reps: row.get(11)?,
        lapses: row.get(12)?,
        left: row.get(13)?,
        odue: row.get(14).ok().unwrap_or_default(),
        odid: row.get(15)?,
        flags: row.get(16)?,
        data: row.get(17)?,
    })
}

impl super::SqliteStorage {
    pub fn get_card(&self, cid: CardID) -> Result<Option<Card>> {
        self.db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where id = ?"))?
            .query_row(params![cid], row_to_card)
            .optional()
            .map_err(Into::into)
    }

    pub(crate) fn update_card(&self, card: &Card) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("update_card.sql"))?;
        stmt.execute(params![
            card.nid,
            card.did,
            card.ord,
            card.mtime,
            card.usn,
            card.ctype as u8,
            card.queue as i8,
            card.due,
            card.ivl,
            card.factor,
            card.reps,
            card.lapses,
            card.left,
            card.odue,
            card.odid,
            card.flags,
            card.data,
            card.id,
        ])?;
        Ok(())
    }

    pub(crate) fn add_card(&self, card: &mut Card) -> Result<()> {
        let now = TimestampMillis::now().0;
        let mut stmt = self.db.prepare_cached(include_str!("add_card.sql"))?;
        stmt.execute(params![
            now,
            card.nid,
            card.did,
            card.ord,
            card.mtime,
            card.usn,
            card.ctype as u8,
            card.queue as i8,
            card.due,
            card.ivl,
            card.factor,
            card.reps,
            card.lapses,
            card.left,
            card.odue,
            card.odid,
            card.flags,
            card.data,
        ])?;
        card.id = CardID(self.db.last_insert_rowid());
        Ok(())
    }

    pub(crate) fn remove_card(&self, cid: CardID) -> Result<()> {
        self.db
            .prepare_cached("delete from cards where id = ?")?
            .execute(&[cid])?;
        Ok(())
    }

    /// Fix some invalid card properties, and return number of changed cards.
    pub(crate) fn fix_card_properties(
        &self,
        today: u32,
        mtime: TimestampSecs,
        usn: Usn,
    ) -> Result<(usize, usize)> {
        let new_cnt = self
            .db
            .prepare(include_str!("fix_due_new.sql"))?
            .execute(params![mtime, usn])?;
        let mut other_cnt = self
            .db
            .prepare(include_str!("fix_due_other.sql"))?
            .execute(params![mtime, usn, today])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_odue.sql"))?
            .execute(params![mtime, usn])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_ivl.sql"))?
            .execute(params![mtime, usn])?;
        Ok((new_cnt, other_cnt))
    }

    pub(crate) fn delete_orphaned_cards(&self) -> Result<usize> {
        self.db
            .prepare("delete from cards where nid not in (select id from notes)")?
            .execute(NO_PARAMS)
            .map_err(Into::into)
    }

    pub(crate) fn all_filtered_cards_by_deck(&self) -> Result<Vec<(CardID, DeckID)>> {
        self.db
            .prepare("select id, did from cards where odid > 0")?
            .query_and_then(NO_PARAMS, |r| -> Result<_> { Ok((r.get(0)?, r.get(1)?)) })?
            .collect()
    }

    pub(crate) fn max_new_card_position(&self) -> Result<u32> {
        self.db
            .prepare("select max(due)+1 from cards where type=0")?
            .query_row(NO_PARAMS, |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn get_card_by_ordinal(&self, nid: NoteID, ord: u16) -> Result<Option<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                " where nid = ? and ord = ?"
            ))?
            .query_row(params![nid, ord], row_to_card)
            .optional()
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_card_usns(&self) -> Result<()> {
        self.db
            .prepare("update cards set usn = 0 where usn = -1")?
            .execute(NO_PARAMS)?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::{card::Card, i18n::I18n, log, storage::SqliteStorage};
    use std::path::Path;

    #[test]
    fn add_card() {
        let i18n = I18n::new(&[""], "", log::terminal());
        let storage = SqliteStorage::open_or_create(Path::new(":memory:"), &i18n).unwrap();
        let mut card = Card::default();
        storage.add_card(&mut card).unwrap();
        let id1 = card.id;
        storage.add_card(&mut card).unwrap();
        assert_ne!(id1, card.id);
    }
}
