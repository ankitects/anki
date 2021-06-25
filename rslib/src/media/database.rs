// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, path::Path};

use rusqlite::{params, Connection, OptionalExtension, Row, Statement};

use crate::error::Result;

fn trace(s: &str) {
    println!("sql: {}", s)
}

pub(super) fn open_or_create<P: AsRef<Path>>(path: P) -> Result<Connection> {
    let mut db = Connection::open(path)?;

    if std::env::var("TRACESQL").is_ok() {
        db.trace(Some(trace));
    }

    db.pragma_update(None, "page_size", &4096)?;
    db.pragma_update(None, "legacy_file_format", &false)?;
    db.pragma_update_and_check(None, "journal_mode", &"wal", |_| Ok(()))?;

    initial_db_setup(&mut db)?;

    Ok(db)
}

fn initial_db_setup(db: &mut Connection) -> Result<()> {
    // tables already exist?
    if db
        .prepare("select null from sqlite_master where type = 'table' and name = 'media'")?
        .exists([])?
    {
        return Ok(());
    }

    db.execute("begin", [])?;
    db.execute_batch(include_str!("schema.sql"))?;
    db.execute_batch("commit; vacuum; analyze;")?;

    Ok(())
}

#[derive(Debug, PartialEq)]
pub struct MediaEntry {
    pub fname: String,
    /// If None, file has been deleted
    pub sha1: Option<[u8; 20]>,
    // Modification time; 0 if deleted
    pub mtime: i64,
    /// True if changed since last sync
    pub sync_required: bool,
}

#[derive(Debug, PartialEq)]
pub struct MediaDatabaseMetadata {
    pub folder_mtime: i64,
    pub last_sync_usn: i32,
}

/// Helper to prepare a statement, or return a previously prepared one.
macro_rules! cached_sql {
    ( $label:expr, $db:expr, $sql:expr ) => {{
        if $label.is_none() {
            $label = Some($db.prepare($sql)?);
        }
        $label.as_mut().unwrap()
    }};
}

pub struct MediaDatabaseContext<'a> {
    db: &'a Connection,

    get_entry_stmt: Option<Statement<'a>>,
    update_entry_stmt: Option<Statement<'a>>,
    remove_entry_stmt: Option<Statement<'a>>,
}

impl MediaDatabaseContext<'_> {
    pub(super) fn new(db: &Connection) -> MediaDatabaseContext {
        MediaDatabaseContext {
            db,
            get_entry_stmt: None,
            update_entry_stmt: None,
            remove_entry_stmt: None,
        }
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(super) fn transact<F, R>(&mut self, func: F) -> Result<R>
    where
        F: FnOnce(&mut MediaDatabaseContext) -> Result<R>,
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

    fn begin(&mut self) -> Result<()> {
        self.db.execute_batch("begin immediate").map_err(Into::into)
    }

    fn commit(&mut self) -> Result<()> {
        self.db.execute_batch("commit").map_err(Into::into)
    }

    fn rollback(&mut self) -> Result<()> {
        self.db.execute_batch("rollback").map_err(Into::into)
    }

    pub(super) fn get_entry(&mut self, fname: &str) -> Result<Option<MediaEntry>> {
        let stmt = cached_sql!(
            self.get_entry_stmt,
            self.db,
            "
select fname, csum, mtime, dirty from media where fname=?"
        );

        stmt.query_row(params![fname], row_to_entry)
            .optional()
            .map_err(Into::into)
    }

    pub(super) fn set_entry(&mut self, entry: &MediaEntry) -> Result<()> {
        let stmt = cached_sql!(
            self.update_entry_stmt,
            self.db,
            "
insert or replace into media (fname, csum, mtime, dirty)
values (?, ?, ?, ?)"
        );

        let sha1_str = entry.sha1.map(hex::encode);
        stmt.execute(params![
            entry.fname,
            sha1_str,
            entry.mtime,
            entry.sync_required
        ])?;

        Ok(())
    }

    pub(super) fn remove_entry(&mut self, fname: &str) -> Result<()> {
        let stmt = cached_sql!(
            self.remove_entry_stmt,
            self.db,
            "
delete from media where fname=?"
        );

        stmt.execute(params![fname])?;

        Ok(())
    }

    pub(super) fn get_meta(&mut self) -> Result<MediaDatabaseMetadata> {
        let mut stmt = self.db.prepare("select dirMod, lastUsn from meta")?;

        stmt.query_row([], |row| {
            Ok(MediaDatabaseMetadata {
                folder_mtime: row.get(0)?,
                last_sync_usn: row.get(1)?,
            })
        })
        .map_err(Into::into)
    }

    pub(super) fn set_meta(&mut self, meta: &MediaDatabaseMetadata) -> Result<()> {
        let mut stmt = self.db.prepare("update meta set dirMod = ?, lastUsn = ?")?;
        stmt.execute(params![meta.folder_mtime, meta.last_sync_usn])?;

        Ok(())
    }

    pub(super) fn count(&mut self) -> Result<u32> {
        self.db
            .query_row(
                "select count(*) from media where csum is not null",
                [],
                |row| row.get(0),
            )
            .map_err(Into::into)
    }

    pub(super) fn get_pending_uploads(&mut self, max_entries: u32) -> Result<Vec<MediaEntry>> {
        let mut stmt = self
            .db
            .prepare("select fname from media where dirty=1 limit ?")?;
        let results: Result<Vec<_>> = stmt
            .query_and_then(params![max_entries], |row| {
                let fname = row.get_ref_unwrap(0).as_str()?;
                Ok(self.get_entry(fname)?.unwrap())
            })?
            .collect();

        results
    }

    pub(super) fn all_mtimes(&mut self) -> Result<HashMap<String, i64>> {
        let mut stmt = self
            .db
            .prepare("select fname, mtime from media where csum is not null")?;
        let map: std::result::Result<HashMap<String, i64>, rusqlite::Error> = stmt
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect();
        Ok(map?)
    }

    pub(super) fn force_resync(&mut self) -> Result<()> {
        self.db
            .execute_batch("delete from media; update meta set lastUsn = 0, dirMod = 0")
            .map_err(Into::into)
    }
}

fn row_to_entry(row: &Row) -> rusqlite::Result<MediaEntry> {
    // map the string checksum into bytes
    let sha1_str: Option<String> = row.get(1)?;
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

#[cfg(test)]
mod test {
    use tempfile::NamedTempFile;

    use crate::{
        error::Result,
        media::{database::MediaEntry, files::sha1_of_data, MediaManager},
    };

    #[test]
    fn database() -> Result<()> {
        let db_file = NamedTempFile::new()?;
        let db_file_path = db_file.path().to_str().unwrap();
        let mut mgr = MediaManager::new("/dummy", db_file_path)?;
        let mut ctx = mgr.dbctx();

        ctx.transact(|ctx| {
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
            assert_eq!(meta.last_sync_usn, 0);

            meta.folder_mtime = 123;
            meta.last_sync_usn = 321;

            ctx.set_meta(&meta)?;

            meta = ctx.get_meta()?;
            assert_eq!(meta.folder_mtime, 123);
            assert_eq!(meta.last_sync_usn, 321);

            Ok(())
        })?;

        // reopen database and ensure data was committed
        drop(ctx);
        drop(mgr);
        mgr = MediaManager::new("/dummy", db_file_path)?;
        let mut ctx = mgr.dbctx();
        let meta = ctx.get_meta()?;
        assert_eq!(meta.folder_mtime, 123);

        Ok(())
    }
}
