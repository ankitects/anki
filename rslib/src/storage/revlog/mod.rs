// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::prelude::*;
use crate::{err::Result, sync::ReviewLogEntry};
use rusqlite::{params, NO_PARAMS};

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

    pub(crate) fn add_revlog_entry(&self, entry: &ReviewLogEntry) -> Result<()> {
        self.db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                entry.id,
                entry.cid,
                entry.usn,
                entry.ease,
                entry.interval,
                entry.last_interval,
                entry.factor,
                entry.time,
                entry.kind
            ])?;
        Ok(())
    }

    pub(crate) fn take_revlog_pending_sync(
        &self,
        new_usn: Usn,
        limit: usize,
    ) -> Result<Vec<ReviewLogEntry>> {
        let entries: Vec<ReviewLogEntry> = self
            .db
            .prepare_cached(concat!(include_str!("get.sql"), " where usn=-1 limit ?"))?
            .query_and_then(&[limit as u32], |row| {
                Ok(ReviewLogEntry {
                    id: row.get(0)?,
                    cid: row.get(1)?,
                    usn: row.get(2)?,
                    ease: row.get(3)?,
                    interval: row.get(4)?,
                    last_interval: row.get(5)?,
                    factor: row.get(6)?,
                    time: row.get(7)?,
                    kind: row.get(8)?,
                })
            })?
            .collect::<Result<_>>()?;

        let mut stmt = self
            .db
            .prepare_cached("update revlog set usn=? where id=?")?;
        for entry in &entries {
            stmt.execute(params![new_usn, entry.id])?;
        }

        Ok(entries)
    }
}
