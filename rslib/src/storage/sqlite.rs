// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::collection::CollectionOp;
use crate::err::Result;
use crate::err::{AnkiError, DBErrorKind};
use crate::time::{i64_unix_millis, i64_unix_secs};
use crate::types::Usn;
use rusqlite::{params, Connection, NO_PARAMS};
use std::path::{Path, PathBuf};

const SCHEMA_MIN_VERSION: u8 = 11;
const SCHEMA_MAX_VERSION: u8 = 11;

// currently public for dbproxy
#[derive(Debug)]
pub struct SqliteStorage {
    // currently crate-visible for dbproxy
    pub(crate) db: Connection,

    // fixme: stored in wrong location?
    path: PathBuf,
}

fn open_or_create_collection_db(path: &Path) -> Result<Connection> {
    let mut db = Connection::open(path)?;

    if std::env::var("TRACESQL").is_ok() {
        db.trace(Some(trace));
    }

    db.busy_timeout(std::time::Duration::from_secs(0))?;

    db.pragma_update(None, "locking_mode", &"exclusive")?;
    db.pragma_update(None, "page_size", &4096)?;
    db.pragma_update(None, "cache_size", &(-40 * 1024))?;
    db.pragma_update(None, "legacy_file_format", &false)?;
    db.pragma_update(None, "journal", &"wal")?;
    db.set_prepared_statement_cache_capacity(50);

    Ok(db)
}

/// Fetch schema version from database.
/// Return (must_create, version)
fn schema_version(db: &Connection) -> Result<(bool, u8)> {
    if !db
        .prepare("select null from sqlite_master where type = 'table' and name = 'col'")?
        .exists(NO_PARAMS)?
    {
        return Ok((true, SCHEMA_MAX_VERSION));
    }

    Ok((
        false,
        db.query_row("select ver from col", NO_PARAMS, |r| Ok(r.get(0)?))?,
    ))
}

fn trace(s: &str) {
    println!("sql: {}", s)
}

impl SqliteStorage {
    pub(crate) fn open_or_create(path: &Path) -> Result<Self> {
        let db = open_or_create_collection_db(path)?;

        let (create, ver) = schema_version(&db)?;
        if create {
            db.prepare_cached("begin exclusive")?.execute(NO_PARAMS)?;
            db.execute_batch(include_str!("schema11.sql"))?;
            db.execute("update col set crt=?, ver=?", params![i64_unix_secs(), ver])?;
            db.prepare_cached("commit")?.execute(NO_PARAMS)?;
        } else {
            if ver > SCHEMA_MAX_VERSION {
                return Err(AnkiError::DBError {
                    info: "".to_string(),
                    kind: DBErrorKind::FileTooNew,
                });
            }
            if ver < SCHEMA_MIN_VERSION {
                return Err(AnkiError::DBError {
                    info: "".to_string(),
                    kind: DBErrorKind::FileTooOld,
                });
            }
        };

        let storage = Self {
            db,
            path: path.to_owned(),
        };

        Ok(storage)
    }

    pub(crate) fn context(&self, server: bool) -> StorageContext {
        StorageContext::new(&self.db, server)
    }
}

pub(crate) struct StorageContext<'a> {
    pub(crate) db: &'a Connection,
    #[allow(dead_code)]
    server: bool,
    #[allow(dead_code)]
    usn: Option<Usn>,
}

impl StorageContext<'_> {
    fn new(db: &Connection, server: bool) -> StorageContext {
        StorageContext {
            db,
            server,
            usn: None,
        }
    }

    // Standard transaction start/stop
    //////////////////////////////////////

    pub(crate) fn begin_trx(&self) -> Result<()> {
        self.db
            .prepare_cached("begin exclusive")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn commit_trx(&self) -> Result<()> {
        if !self.db.is_autocommit() {
            self.db.prepare_cached("commit")?.execute(NO_PARAMS)?;
        }
        Ok(())
    }

    pub(crate) fn rollback_trx(&self) -> Result<()> {
        if !self.db.is_autocommit() {
            self.db.execute("rollback", NO_PARAMS)?;
        }
        Ok(())
    }

    // Savepoints
    //////////////////////////////////////////
    //
    // This is necessary at the moment because Anki's current architecture uses
    // long-running transactions as an undo mechanism. Once a proper undo
    // mechanism has been added to all existing functionality, we could
    // transition these to standard commits.

    pub(crate) fn begin_rust_trx(&self) -> Result<()> {
        self.db
            .prepare_cached("savepoint rust")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn commit_rust_trx(&self) -> Result<()> {
        self.db.prepare_cached("release rust")?.execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn commit_rust_op(&self, _op: Option<CollectionOp>) -> Result<()> {
        self.commit_rust_trx()
    }

    pub(crate) fn rollback_rust_trx(&self) -> Result<()> {
        self.db
            .prepare_cached("rollback to rust")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    //////////////////////////////////////////

    pub(crate) fn mark_modified(&self) -> Result<()> {
        self.db
            .prepare_cached("update col set mod=?")?
            .execute(params![i64_unix_millis()])?;
        Ok(())
    }

    #[allow(dead_code)]
    pub(crate) fn usn(&mut self) -> Result<Usn> {
        if self.server {
            if self.usn.is_none() {
                self.usn = Some(
                    self.db
                        .prepare_cached("select usn from col")?
                        .query_row(NO_PARAMS, |row| row.get(0))?,
                );
            }
            Ok(*self.usn.as_ref().unwrap())
        } else {
            Ok(-1)
        }
    }
}
