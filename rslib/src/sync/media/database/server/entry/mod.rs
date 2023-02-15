// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::mem;

use rusqlite::params;
use rusqlite::OptionalExtension;
use rusqlite::Row;

use crate::error;
use crate::prelude::TimestampSecs;
use crate::prelude::Usn;
use crate::sync::media::database::server::meta::StoreMetadata;
use crate::sync::media::database::server::ServerMediaDatabase;

pub mod changes;
mod download;
pub mod upload;

impl ServerMediaDatabase {
    /// Does not return a deletion entry.
    pub fn get_nonempty_entry(&self, nfc_filename: &str) -> error::Result<Option<MediaEntry>> {
        self.get_entry(nfc_filename)
            .map(|e| e.filter(|e| !e.is_deleted()))
    }

    pub fn get_entry(&self, nfc_filename: &str) -> error::Result<Option<MediaEntry>> {
        self.db
            .prepare_cached(include_str!("get_entry.sql"))?
            .query_row([nfc_filename], MediaEntry::from_row)
            .optional()
            .map_err(Into::into)
    }

    /// Saves entry to the DB, overwriting any existing entry. Does no
    /// validation on its own; caller is responsible for mutating meta
    /// (which will update mtime as well).
    pub fn set_entry(&mut self, entry: &mut MediaEntry) -> error::Result<()> {
        self.db
            .prepare_cached(include_str!("set_entry.sql"))?
            .execute(params![
                &entry.nfc_filename,
                &entry.sha1,
                &entry.size,
                &entry.usn,
                &entry.mtime.0,
            ])?;
        Ok(())
    }

    fn add_entry(
        &mut self,
        meta: &mut StoreMetadata,
        filename: String,
        total_bytes: usize,
        sha1: Vec<u8>,
    ) -> error::Result<MediaEntry> {
        assert!(total_bytes > 0);
        let mut new_entry = MediaEntry {
            nfc_filename: filename,
            sha1,
            size: total_bytes as u64,
            // set by following call
            usn: Default::default(),
            mtime: Default::default(),
        };
        meta.add_entry(&mut new_entry);
        self.set_entry(&mut new_entry)?;
        Ok(new_entry)
    }

    /// Returns the old sha1
    fn replace_entry(
        &mut self,
        meta: &mut StoreMetadata,
        existing_nonempty: &mut MediaEntry,
        total_bytes: usize,
        sha1: Vec<u8>,
    ) -> error::Result<Vec<u8>> {
        assert!(total_bytes > 0);
        meta.replace_entry(existing_nonempty, total_bytes as u64);
        let old_sha1 = mem::replace(&mut existing_nonempty.sha1, sha1);
        self.set_entry(existing_nonempty)?;
        Ok(old_sha1)
    }

    fn remove_entry(
        &mut self,
        meta: &mut StoreMetadata,
        existing_nonempty: &mut MediaEntry,
    ) -> error::Result<()> {
        meta.remove_entry(existing_nonempty);
        self.set_entry(existing_nonempty)?;
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MediaEntry {
    pub nfc_filename: String,
    pub sha1: Vec<u8>,
    /// Set to 0 to indicate deletion.
    pub size: u64,
    pub usn: Usn,
    pub mtime: TimestampSecs,
}

impl MediaEntry {
    pub fn from_row(row: &Row) -> std::result::Result<Self, rusqlite::Error> {
        Ok(Self {
            nfc_filename: row.get(0)?,
            sha1: row.get(1)?,
            size: row.get(2)?,
            usn: row.get(3)?,
            mtime: TimestampSecs(row.get(4)?),
        })
    }

    fn is_deleted(&self) -> bool {
        self.size == 0
    }
}
