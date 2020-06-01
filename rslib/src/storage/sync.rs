// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::*;
use crate::prelude::*;
use rusqlite::{params, types::FromSql, ToSql, NO_PARAMS};

impl SqliteStorage {
    pub(crate) fn usn(&self, server: bool) -> Result<Usn> {
        if server {
            Ok(Usn(self
                .db
                .prepare_cached("select usn from col")?
                .query_row(NO_PARAMS, |row| row.get(0))?))
        } else {
            Ok(Usn(-1))
        }
    }

    pub(crate) fn set_usn(&self, usn: Usn) -> Result<()> {
        self.db
            .prepare_cached("update col set usn = ?")?
            .execute(&[usn])?;
        Ok(())
    }

    pub(crate) fn increment_usn(&self) -> Result<()> {
        self.db
            .prepare_cached("update col set usn = usn + 1")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn objects_pending_sync<T: FromSql>(&self, table: &str, usn: Usn) -> Result<Vec<T>> {
        self.db
            .prepare_cached(&format!(
                "select id from {} where {}",
                table,
                usn.pending_object_clause()
            ))?
            .query_and_then(&[usn], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn maybe_update_object_usns<I: ToSql>(
        &self,
        table: &str,
        ids: &[I],
        new_usn: Option<Usn>,
    ) -> Result<()> {
        if let Some(new_usn) = new_usn {
            let mut stmt = self
                .db
                .prepare_cached(&format!("update {} set usn=? where id=?", table))?;
            for id in ids {
                stmt.execute(params![new_usn, id])?;
            }
        }
        Ok(())
    }
}
