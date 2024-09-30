// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::params;
use rusqlite::Row;

use crate::error;
use crate::prelude::TimestampSecs;
use crate::prelude::Usn;
use crate::sync::media::database::server::entry::MediaEntry;
use crate::sync::media::database::server::ServerMediaDatabase;

#[derive(Debug, PartialEq, Eq)]
pub struct StoreMetadata {
    pub last_usn: Usn,
    pub total_bytes: u64,
    pub total_nonempty_files: u32,
}

impl StoreMetadata {
    pub(crate) fn add_entry(&mut self, entry: &mut MediaEntry) {
        assert!(entry.size > 0);
        self.total_bytes += entry.size;
        self.total_nonempty_files += 1;
        entry.usn = self.next_usn();
        entry.mtime = TimestampSecs::now();
    }

    /// Expects entry to have its old size; the new size will be set.
    pub(crate) fn replace_entry(&mut self, entry: &mut MediaEntry, new_size: u64) {
        assert!(entry.size > 0);
        assert!(new_size > 0);
        self.total_bytes = self.total_bytes.saturating_sub(entry.size) + new_size;
        entry.size = new_size;
        entry.usn = self.next_usn();
        entry.mtime = TimestampSecs::now();
    }

    pub(crate) fn remove_entry(&mut self, entry: &mut MediaEntry) {
        assert!(entry.size > 0);
        self.total_bytes = self.total_bytes.saturating_sub(entry.size);
        self.total_nonempty_files = self.total_nonempty_files.saturating_sub(1);
        entry.size = 0;
        entry.usn = self.next_usn();
        entry.mtime = TimestampSecs::now();
    }
}

impl StoreMetadata {
    fn from_row(row: &Row) -> error::Result<Self, rusqlite::Error> {
        Ok(Self {
            last_usn: row.get(0)?,
            total_bytes: row.get(1)?,
            total_nonempty_files: row.get(2)?,
        })
    }

    fn next_usn(&mut self) -> Usn {
        self.last_usn.0 += 1;
        self.last_usn
    }
}

impl ServerMediaDatabase {
    /// Perform an exclusive transaction. Will implicitly commit if no error
    /// returned, after flushing the updated metadata. Returns the latest
    /// usn.
    pub fn with_transaction<F>(&mut self, op: F) -> error::Result<Usn>
    where
        F: FnOnce(&mut Self, &mut StoreMetadata) -> error::Result<()>,
    {
        self.db.execute("begin exclusive", [])?;
        let mut meta = self.get_meta()?;
        op(self, &mut meta)
            .and_then(|_| {
                self.set_meta(&meta)?;
                self.db.execute("commit", [])?;
                Ok(meta.last_usn)
            })
            .inspect_err(|_e| {
                let _ = self.db.execute("rollback", []);
            })
    }

    pub fn last_usn(&self) -> error::Result<Usn> {
        Ok(self.get_meta()?.last_usn)
    }

    fn get_meta(&self) -> error::Result<StoreMetadata> {
        self.db
            .prepare_cached(include_str!("get_meta.sql"))?
            .query_row([], StoreMetadata::from_row)
            .map_err(Into::into)
    }

    fn set_meta(&mut self, meta: &StoreMetadata) -> error::Result<()> {
        self.db
            .prepare_cached(include_str!("set_meta.sql"))?
            .execute(params![
                meta.last_usn,
                meta.total_bytes,
                meta.total_nonempty_files
            ])?;
        Ok(())
    }

    pub fn nonempty_file_count(&self) -> error::Result<u32> {
        Ok(self.get_meta()?.total_nonempty_files)
    }

    pub fn total_bytes(&self) -> error::Result<u64> {
        Ok(self.get_meta()?.total_bytes)
    }
}
