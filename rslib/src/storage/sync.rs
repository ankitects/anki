// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::params;
use rusqlite::types::FromSql;
use rusqlite::ToSql;

use super::*;
use crate::prelude::*;

impl SqliteStorage {
    pub(crate) fn usn(&self, server: bool) -> Result<Usn> {
        if server {
            Ok(Usn(self
                .db
                .prepare_cached("select usn from col")?
                .query_row([], |row| row.get(0))?))
        } else {
            Ok(Usn(-1))
        }
    }

    pub(crate) fn set_usn(&self, usn: Usn) -> Result<()> {
        self.db
            .prepare_cached("update col set usn = ?")?
            .execute([usn])?;
        Ok(())
    }

    pub(crate) fn increment_usn(&self) -> Result<()> {
        self.db
            .prepare_cached("update col set usn = usn + 1")?
            .execute([])?;
        Ok(())
    }

    pub(crate) fn objects_pending_sync<T: FromSql>(&self, table: &str, usn: Usn) -> Result<Vec<T>> {
        self.db
            .prepare_cached(&format!(
                "select id from {} where {}",
                table,
                usn.pending_object_clause()
            ))?
            .query_and_then([usn], |r| r.get(0).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn maybe_update_object_usns<I: ToSql>(
        &self,
        table: &str,
        ids: &[I],
        server_usn_if_client: Option<Usn>,
    ) -> Result<()> {
        if let Some(new_usn) = server_usn_if_client {
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

impl Usn {
    /// Used when gathering pending objects during sync.
    pub(crate) fn pending_object_clause(self) -> &'static str {
        if self.0 == -1 {
            "usn = ?"
        } else {
            "usn >= ?"
        }
    }
}
