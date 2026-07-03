// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::params;

use super::SqliteStorage;
use crate::collection::timestamps::CollectionTimestamps;
use crate::prelude::*;

impl SqliteStorage {
    pub(crate) fn get_collection_timestamps(&self) -> Result<CollectionTimestamps> {
        self.db
            .prepare_cached("select mod, scm, ls from col")?
            .query_row([], |row| {
                Ok(CollectionTimestamps {
                    collection_change: row.get(0)?,
                    schema_change: row.get(1)?,
                    last_sync: row.get(2)?,
                })
            })
            .map_err(Into::into)
    }

    pub(crate) fn set_schema_modified_time(&self, stamp: TimestampMillis) -> Result<()> {
        self.db
            .prepare_cached("update col set scm = ?")?
            .execute([stamp])?;
        Ok(())
    }

    pub(crate) fn set_last_sync(&self, stamp: TimestampMillis) -> Result<()> {
        self.db.prepare("update col set ls = ?")?.execute([stamp])?;
        Ok(())
    }

    pub(crate) fn set_modified_time(&self, stamp: TimestampMillis) -> Result<()> {
        self.db
            .prepare_cached("update col set mod=?")?
            .execute(params![stamp])?;
        Ok(())
    }

    // Creation timestamp is used less frequently, and has separate accessor

    pub(crate) fn creation_stamp(&self) -> Result<TimestampSecs> {
        self.db
            .prepare_cached("select crt from col")?
            .query_row([], |row| row.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn set_creation_stamp(&self, stamp: TimestampSecs) -> Result<()> {
        self.db
            .prepare("update col set crt = ?")?
            .execute([stamp])?;
        Ok(())
    }
}
