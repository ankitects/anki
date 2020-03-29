// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::{Card, CardID, CardQueue, CardType};
use crate::err::Result;
use crate::timestamp::TimestampMillis;
use rusqlite::params;
use rusqlite::{
    types::{FromSql, FromSqlError, ValueRef},
    OptionalExtension,
};
use std::convert::TryFrom;

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

impl super::SqliteStorage {
    pub fn get_card(&self, cid: CardID) -> Result<Option<Card>> {
        let mut stmt = self.db.prepare_cached(include_str!("get_card.sql"))?;
        stmt.query_row(params![cid], |row| {
            Ok(Card {
                id: cid,
                nid: row.get(0)?,
                did: row.get(1)?,
                ord: row.get(2)?,
                mtime: row.get(3)?,
                usn: row.get(4)?,
                ctype: row.get(5)?,
                queue: row.get(6)?,
                due: row.get(7)?,
                ivl: row.get(8)?,
                factor: row.get(9)?,
                reps: row.get(10)?,
                lapses: row.get(11)?,
                left: row.get(12)?,
                odue: row.get(13)?,
                odid: row.get(14)?,
                flags: row.get(15)?,
                data: row.get(16)?,
            })
        })
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
}

#[cfg(test)]
mod test {
    use crate::{card::Card, storage::SqliteStorage};
    use std::path::Path;

    #[test]
    fn add_card() {
        let storage = SqliteStorage::open_or_create(Path::new(":memory:")).unwrap();
        let mut card = Card::default();
        storage.add_card(&mut card).unwrap();
        let id1 = card.id;
        storage.add_card(&mut card).unwrap();
        assert_ne!(id1, card.id);
    }
}
