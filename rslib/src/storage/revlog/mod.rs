// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::err::Result;
use crate::{
    backend_proto as pb,
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
};
use rusqlite::{
    params,
    types::{FromSql, FromSqlError, ValueRef},
    Row, NO_PARAMS,
};
use std::convert::TryFrom;

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
            .execute(NO_PARAMS)
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_revlog_usns(&self) -> Result<()> {
        self.db
            .prepare("update revlog set usn = 0 where usn = -1")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn add_revlog_entry(&self, entry: &RevlogEntry) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
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
        Ok(())
    }

    pub(crate) fn get_revlog_entry(&self, id: RevlogID) -> Result<Option<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id=?"))?
            .query_and_then(&[id], row_to_revlog_entry)?
            .next()
            .transpose()
    }

    pub(crate) fn get_revlog_entries_for_card(&self, cid: CardID) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where cid=?"))?
            .query_and_then(&[cid], row_to_revlog_entry)?
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
            .query_and_then(&[after.0 * 1000], |r| {
                row_to_revlog_entry(r).map(Into::into)
            })?
            .collect()
    }

    /// This includes entries from deleted cards.
    pub(crate) fn get_all_revlog_entries(
        &self,
        after: TimestampSecs,
    ) -> Result<Vec<pb::RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id >= ?"))?
            .query_and_then(&[after.0 * 1000], |r| {
                row_to_revlog_entry(r).map(Into::into)
            })?
            .collect()
    }

    pub(crate) fn studied_today(&self, day_cutoff: i64) -> Result<StudiedToday> {
        let start = (day_cutoff - 86_400) * 1_000;
        self.db
            .prepare_cached(include_str!("studied_today.sql"))?
            .query_map(&[start, RevlogReviewKind::Manual as i64], |row| {
                Ok(StudiedToday {
                    cards: row.get(0)?,
                    seconds: row.get(1)?,
                })
            })?
            .next()
            .unwrap()
            .map_err(Into::into)
    }
}
