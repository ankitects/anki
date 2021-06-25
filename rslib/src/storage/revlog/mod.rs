// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use rusqlite::{
    params,
    types::{FromSql, FromSqlError, ValueRef},
    Row,
};

use super::SqliteStorage;
use crate::{
    backend_proto as pb,
    error::Result,
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
};

pub(crate) struct StudiedToday {
    pub cards: u32,
    pub seconds: f64,
}

impl FromSql for RevlogReviewKind {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_revlog_entry(row: &Row) -> Result<RevlogEntry> {
    Ok(RevlogEntry {
        id: row.get(0)?,
        cid: row.get(1)?,
        usn: row.get(2)?,
        button_chosen: row.get(3)?,
        interval: row.get(4)?,
        last_interval: row.get(5)?,
        ease_factor: row.get(6)?,
        taken_millis: row.get(7).unwrap_or_default(),
        review_kind: row.get(8).unwrap_or_default(),
    })
}

impl SqliteStorage {
    pub(crate) fn fix_revlog_properties(&self) -> Result<usize> {
        self.db
            .prepare(include_str!("fix_props.sql"))?
            .execute([])
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_revlog_usns(&self) -> Result<()> {
        self.db
            .prepare("update revlog set usn = 0 where usn = -1")?
            .execute([])?;
        Ok(())
    }

    /// Returns the used id, which may differ if `ensure_unique` is true.
    pub(crate) fn add_revlog_entry(
        &self,
        entry: &RevlogEntry,
        ensure_unique: bool,
    ) -> Result<RevlogId> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                ensure_unique,
                entry.id,
                entry.cid,
                entry.usn,
                entry.button_chosen,
                entry.interval,
                entry.last_interval,
                entry.ease_factor,
                entry.taken_millis,
                entry.review_kind as u8
            ])?;
        Ok(RevlogId(self.db.last_insert_rowid()))
    }

    pub(crate) fn get_revlog_entry(&self, id: RevlogId) -> Result<Option<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id=?"))?
            .query_and_then([id], row_to_revlog_entry)?
            .next()
            .transpose()
    }

    /// Only intended to be used by the undo code, as Anki can not sync revlog deletions.
    pub(crate) fn remove_revlog_entry(&self, id: RevlogId) -> Result<()> {
        self.db
            .prepare_cached("delete from revlog where id = ?")?
            .execute([id])?;
        Ok(())
    }

    pub(crate) fn get_revlog_entries_for_card(&self, cid: CardId) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where cid=?"))?
            .query_and_then([cid], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards(
        &self,
        after: TimestampSecs,
    ) -> Result<Vec<pb::RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids) and id >= ?"
            ))?
            .query_and_then([after.0 * 1000], |r| row_to_revlog_entry(r).map(Into::into))?
            .collect()
    }

    /// This includes entries from deleted cards.
    pub(crate) fn get_all_revlog_entries(
        &self,
        after: TimestampSecs,
    ) -> Result<Vec<pb::RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id >= ?"))?
            .query_and_then([after.0 * 1000], |r| row_to_revlog_entry(r).map(Into::into))?
            .collect()
    }

    pub(crate) fn studied_today(&self, day_cutoff: TimestampSecs) -> Result<StudiedToday> {
        let start = day_cutoff.adding_secs(-86_400).as_millis();
        self.db
            .prepare_cached(include_str!("studied_today.sql"))?
            .query_map([start.0, RevlogReviewKind::Manual as i64], |row| {
                Ok(StudiedToday {
                    cards: row.get(0)?,
                    seconds: row.get(1)?,
                })
            })?
            .next()
            .unwrap()
            .map_err(Into::into)
    }

    pub(crate) fn upgrade_revlog_to_v2(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("v2_upgrade.sql"))
            .map_err(Into::into)
    }
}
