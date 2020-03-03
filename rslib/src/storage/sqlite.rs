// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::err::{AnkiError, DBErrorKind};
use crate::time::i64_unix_timestamp;
use rusqlite::types::{FromSql, FromSqlError, FromSqlResult, ValueRef};
use rusqlite::{params, Connection, OptionalExtension, NO_PARAMS};
use serde::de::DeserializeOwned;
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use std::borrow::Cow;
use std::convert::TryFrom;
use std::fmt;
use std::path::{Path, PathBuf};

const SCHEMA_MIN_VERSION: u8 = 11;
const SCHEMA_MAX_VERSION: u8 = 11;

macro_rules! cached_sql {
    ( $label:expr, $db:expr, $sql:expr ) => {{
        if $label.is_none() {
            $label = Some($db.prepare_cached($sql)?);
        }
        $label.as_mut().unwrap()
    }};
}

#[derive(Debug)]
pub struct SqliteStorage {
    // currently crate-visible for dbproxy
    pub(crate) db: Connection,
    path: PathBuf,
    server: bool,
}

fn open_or_create_collection_db(path: &Path) -> Result<Connection> {
    let mut db = Connection::open(path)?;

    if std::env::var("TRACESQL").is_ok() {
        db.trace(Some(trace));
    }

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
    pub(crate) fn open_or_create(path: &Path, server: bool) -> Result<Self> {
        let db = open_or_create_collection_db(path)?;

        let (create, ver) = schema_version(&db)?;
        if create {
            unimplemented!(); // todo
            db.prepare_cached("begin exclusive")?.execute(NO_PARAMS)?;
            db.execute_batch(include_str!("schema11.sql"))?;
            db.execute(
                "update col set crt=?, ver=?",
                params![i64_unix_timestamp(), ver],
            )?;
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
            server,
        };

        Ok(storage)
    }

    pub(crate) fn begin(&self) -> Result<()> {
        self.db
            .prepare_cached("begin exclusive")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn commit(&self) -> Result<()> {
        self.db.prepare_cached("commit")?.execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn rollback(&self) -> Result<()> {
        self.db.execute("rollback", NO_PARAMS)?;
        Ok(())
    }
}
