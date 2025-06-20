// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::path::Path;

use rusqlite::params;
use rusqlite::Connection;
use rusqlite::OptionalExtension;
use rusqlite::Row;
use tracing::debug;

use crate::error;
use crate::media::files::AddedFile;
use crate::media::Sha1Hash;
use crate::prelude::Usn;
use crate::prelude::*;

pub mod changetracker;

#[derive(Debug, PartialEq, Eq)]
pub struct MediaEntry {
    pub fname: String,
    /// If None, file has been deleted
    pub sha1: Option<Sha1Hash>,
    // Modification time; 0 if deleted
    pub mtime: i64,
    /// True if changed since last sync
    pub sync_required: bool,
}

#[derive(Debug, PartialEq, Eq)]
pub struct MediaDatabaseMetadata {
    pub folder_mtime: i64,
    pub last_sync_usn: Usn,
}

pub struct MediaDatabase {
    db: Connection,
}

impl MediaDatabase {
    pub(crate) fn new(db_path: &Path) -> error::Result<MediaDatabase> {
        Ok(MediaDatabase {
            db: open_or_create(db_path)?,
        })
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact<F, R>(&self, func: F) -> error::Result<R>
    where
        F: FnOnce(&MediaDatabase) -> error::Result<R>,
    {
        self.begin()?;

        let mut res = func(self);

        if res.is_ok() {
            if let Err(e) = self.commit() {
                res = Err(e);
            }
        }

        if res.is_err() {
            self.rollback()?;
        }

        res
    }

    fn begin(&self) -> error::Result<()> {
        self.db.execute_batch("begin immediate").map_err(Into::into)
    }

    fn commit(&self) -> error::Result<()> {
        self.db.execute_batch("commit").map_err(Into::into)
    }

    fn rollback(&self) -> error::Result<()> {
        self.db.execute_batch("rollback").map_err(Into::into)
    }

    pub(crate) fn get_entry(&self, fname: &str) -> error::Result<Option<MediaEntry>> {
        self.db
            .prepare_cached(
                "
select fname, csum, mtime, dirty from media where fname=?",
            )?
            .query_row(params![fname], row_to_entry)
            .optional()
            .map_err(Into::into)
    }

    pub(crate) fn set_entry(&self, entry: &MediaEntry) -> error::Result<()> {
        let sha1_str = entry.sha1.map(hex::encode);
        self.db
            .prepare_cached(
                "
insert or replace into media (fname, csum, mtime, dirty)
values (?, ?, ?, ?)",
            )?
            .execute(params![
                entry.fname,
                sha1_str,
                entry.mtime,
                entry.sync_required
            ])?;

        Ok(())
    }

    pub(crate) fn remove_entry(&self, fname: &str) -> error::Result<()> {
        self.db
            .prepare_cached(
                "
delete from media where fname=?",
            )?
            .execute(params![fname])?;

        Ok(())
    }

    pub(crate) fn get_meta(&self) -> error::Result<MediaDatabaseMetadata> {
        let mut stmt = self.db.prepare("select dirMod, lastUsn from meta")?;

        stmt.query_row([], |row| {
            Ok(MediaDatabaseMetadata {
                folder_mtime: row.get(0)?,
                last_sync_usn: row.get(1)?,
            })
        })
        .map_err(Into::into)
    }

    pub(crate) fn set_meta(&self, meta: &MediaDatabaseMetadata) -> error::Result<()> {
        let mut stmt = self.db.prepare("update meta set dirMod = ?, lastUsn = ?")?;
        stmt.execute(params![meta.folder_mtime, meta.last_sync_usn])?;

        Ok(())
    }

    pub(crate) fn count(&self) -> error::Result<u32> {
        self.db
            .query_row(
                "select count(*) from media where csum is not null",
                [],
                |row| row.get(0),
            )
            .map_err(Into::into)
    }

    pub(crate) fn get_pending_uploads(&self, max_entries: u32) -> error::Result<Vec<MediaEntry>> {
        let mut stmt = self
            .db
            .prepare("select fname from media where dirty=1 limit ?")?;
        let results: error::Result<Vec<_>> = stmt
            .query_and_then(params![max_entries], |row| {
                let fname = row.get_ref_unwrap(0).as_str()?;
                Ok(self.get_entry(fname)?.unwrap())
            })?
            .collect();

        results
    }

    pub(crate) fn all_mtimes(&self) -> error::Result<HashMap<String, i64>> {
        let mut stmt = self
            .db
            .prepare("select fname, mtime from media where csum is not null")?;
        let map: std::result::Result<HashMap<String, i64>, rusqlite::Error> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect();
        Ok(map?)
    }

    /// Returns all filenames and checksums, where the checksum is not null.
    pub(crate) fn all_registered_checksums(&self) -> error::Result<HashMap<String, Sha1Hash>> {
        self.db
            .prepare("SELECT fname, csum FROM media WHERE csum IS NOT NULL")?
            .query_and_then([], row_to_name_and_checksum)?
            .collect()
    }

    pub(crate) fn force_resync(&self) -> error::Result<()> {
        self.db
            .execute_batch("delete from media; update meta set lastUsn = 0, dirMod = 0")
            .map_err(Into::into)
    }

    pub(crate) fn record_clean(&self, clean: &[String]) -> error::Result<()> {
        for fname in clean {
            if let Some(mut entry) = self.get_entry(fname)? {
                if entry.sync_required {
                    entry.sync_required = false;
                    debug!(fname = &entry.fname, "mark clean");
                    self.set_entry(&entry)?;
                }
            }
        }
        Ok(())
    }

    pub fn record_additions(&self, additions: Vec<AddedFile>) -> error::Result<()> {
        for file in additions {
            if let Some(renamed) = file.renamed_from {
                // the file AnkiWeb sent us wasn't normalized, so we need to record
                // the old file name as a deletion
                debug!("marking non-normalized file as deleted: {}", renamed);
                let mut entry = MediaEntry {
                    fname: renamed,
                    sha1: None,
                    mtime: 0,
                    sync_required: true,
                };
                self.set_entry(&entry)?;
                // and upload the new filename to ankiweb
                debug!("marking renamed file as needing upload: {}", file.fname);
                entry = MediaEntry {
                    fname: file.fname.to_string(),
                    sha1: Some(file.sha1),
                    mtime: file.mtime,
                    sync_required: true,
                };
                self.set_entry(&entry)?;
            } else {
                // a normal addition
                let entry = MediaEntry {
                    fname: file.fname.to_string(),
                    sha1: Some(file.sha1),
                    mtime: file.mtime,
                    sync_required: false,
                };
                debug!(
                    fname = &entry.fname,
                    sha1 = hex::encode(&entry.sha1.as_ref().unwrap()[0..4]),
                    "mark added"
                );
                self.set_entry(&entry)?;
            }
        }

        Ok(())
    }

    pub fn record_removals(&self, removals: &[String]) -> error::Result<()> {
        for fname in removals {
            debug!(fname, "mark removed");
            self.remove_entry(fname)?;
        }
        Ok(())
    }
}

fn row_to_entry(row: &Row) -> rusqlite::Result<MediaEntry> {
    // map the string checksum into bytes
    let sha1_str = row.get_ref(1)?.as_str_or_null()?;
    let sha1_array = if let Some(s) = sha1_str {
        let mut arr = [0; 20];
        match hex::decode_to_slice(s, arr.as_mut()) {
            Ok(_) => Some(arr),
            _ => None,
        }
    } else {
        None
    };
    // and return the entry
    Ok(MediaEntry {
        fname: row.get(0)?,
        sha1: sha1_array,
        mtime: row.get(2)?,
        sync_required: row.get(3)?,
    })
}

fn row_to_name_and_checksum(row: &Row) -> error::Result<(String, Sha1Hash)> {
    let file_name = row.get(0)?;
    let sha1_str: String = row.get(1)?;
    let mut sha1 = [0; 20];
    if let Err(err) = hex::decode_to_slice(sha1_str, &mut sha1) {
        invalid_input!(err, "bad media checksum: {file_name}");
    }
    Ok((file_name, sha1))
}

fn trace(event: rusqlite::trace::TraceEvent) {
    if let rusqlite::trace::TraceEvent::Stmt(_, sql) = event {
        println!("sql: {}", sql);
    }
}

pub(crate) fn open_or_create<P: AsRef<Path>>(path: P) -> error::Result<Connection> {
    let mut db = Connection::open(path)?;

    if std::env::var("TRACESQL").is_ok() {
        db.trace_v2(
            rusqlite::trace::TraceEventCodes::SQLITE_TRACE_STMT,
            Some(trace),
        );
    }

    db.pragma_update(None, "page_size", 4096)?;
    db.pragma_update(None, "legacy_file_format", false)?;
    db.pragma_update_and_check(None, "journal_mode", "wal", |_| Ok(()))?;

    initial_db_setup(&mut db)?;

    Ok(db)
}

fn initial_db_setup(db: &mut Connection) -> error::Result<()> {
    // tables already exist?
    if db
        .prepare_cached("select null from sqlite_master where type = 'table' and name = 'media'")?
        .exists([])?
    {
        return Ok(());
    }

    db.execute("begin", [])?;
    db.execute_batch(include_str!("schema.sql"))?;
    db.execute_batch("commit; vacuum; analyze;")?;

    Ok(())
}

#[cfg(test)]
mod test {
    use anki_io::new_tempfile;
    use tempfile::TempDir;

    use crate::error::Result;
    use crate::media::files::sha1_of_data;
    use crate::media::MediaManager;
    use crate::sync::media::database::client::MediaEntry;

    #[test]
    fn database() -> Result<()> {
        let folder = TempDir::new()?;
        let db_file = new_tempfile()?;
        let db_file_path = db_file.path().to_str().unwrap();
        let mut mgr = MediaManager::new(folder.path(), db_file_path)?;
        mgr.db.transact(|ctx| {
            // no entry exists yet
            assert_eq!(ctx.get_entry("test.mp3")?, None);

            // add one
            let mut entry = MediaEntry {
                fname: "test.mp3".into(),
                sha1: None,
                mtime: 0,
                sync_required: false,
            };
            ctx.set_entry(&entry)?;
            assert_eq!(ctx.get_entry("test.mp3")?.unwrap(), entry);

            // update it
            entry.sha1 = Some(sha1_of_data(b"hello"));
            entry.mtime = 123;
            entry.sync_required = true;
            ctx.set_entry(&entry)?;
            assert_eq!(ctx.get_entry("test.mp3")?.unwrap(), entry);

            assert_eq!(ctx.get_pending_uploads(25)?, vec![entry]);

            let mut meta = ctx.get_meta()?;
            assert_eq!(meta.folder_mtime, 0);
            assert_eq!(meta.last_sync_usn.0, 0);

            meta.folder_mtime = 123;
            meta.last_sync_usn.0 = 321;

            ctx.set_meta(&meta)?;

            meta = ctx.get_meta()?;
            assert_eq!(meta.folder_mtime, 123);
            assert_eq!(meta.last_sync_usn.0, 321);

            Ok(())
        })?;

        // reopen database and ensure data was committed
        drop(mgr);
        mgr = MediaManager::new(folder.path(), db_file_path)?;
        let meta = mgr.db.get_meta()?;
        assert_eq!(meta.folder_mtime, 123);

        Ok(())
    }
}
