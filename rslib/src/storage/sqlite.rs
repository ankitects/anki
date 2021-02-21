// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::config::schema11_config_as_string;
use crate::err::Result;
use crate::err::{AnkiError, DBErrorKind};
use crate::timestamp::{TimestampMillis, TimestampSecs};
use crate::{i18n::I18n, sched::cutoff::v1_creation_date, text::without_combining};
use regex::Regex;
use rusqlite::{functions::FunctionFlags, params, Connection, NO_PARAMS};
use std::cmp::Ordering;
use std::{borrow::Cow, path::Path, sync::Arc};
use unicase::UniCase;

use super::upgrades::{SCHEMA_MAX_VERSION, SCHEMA_MIN_VERSION, SCHEMA_STARTING_VERSION};

fn unicase_compare(s1: &str, s2: &str) -> Ordering {
    UniCase::new(s1).cmp(&UniCase::new(s2))
}

// fixme: rollback savepoint when tags not changed
// fixme: need to drop out of wal prior to vacuuming to fix page size of older collections

// currently public for dbproxy
#[derive(Debug)]
pub struct SqliteStorage {
    // currently crate-visible for dbproxy
    pub(crate) db: Connection,
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
    db.pragma_update(None, "journal_mode", &"wal")?;

    db.set_prepared_statement_cache_capacity(50);

    add_field_index_function(&db)?;
    add_regexp_function(&db)?;
    add_without_combining_function(&db)?;

    db.create_collation("unicase", unicase_compare)?;

    Ok(db)
}

/// Adds sql function field_at_index(flds, index)
/// to split provided fields and return field at zero-based index.
/// If out of range, returns empty string.
fn add_field_index_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "field_at_index",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        |ctx| {
            let mut fields = ctx.get_raw(0).as_str()?.split('\x1f');
            let idx: u16 = ctx.get(1)?;
            Ok(fields.nth(idx as usize).unwrap_or("").to_string())
        },
    )
}

fn add_without_combining_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "without_combining",
        1,
        FunctionFlags::SQLITE_DETERMINISTIC,
        |ctx| {
            let text = ctx.get_raw(0).as_str()?;
            Ok(match without_combining(text) {
                Cow::Borrowed(_) => None,
                Cow::Owned(o) => Some(o),
            })
        },
    )
}

/// Adds sql function regexp(regex, string) -> is_match
/// Taken from the rusqlite docs
type BoxError = Box<dyn std::error::Error + Send + Sync + 'static>;
fn add_regexp_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "regexp",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 2, "called with unexpected number of arguments");

            let re: Arc<Regex> = ctx
                .get_or_create_aux(0, |vr| -> std::result::Result<_, BoxError> {
                    Ok(Regex::new(vr.as_str()?)?)
                })?;

            let is_match = {
                let text = ctx
                    .get_raw(1)
                    .as_str()
                    .map_err(|e| rusqlite::Error::UserFunctionError(e.into()))?;

                re.is_match(text)
            };

            Ok(is_match)
        },
    )
}

/// Fetch schema version from database.
/// Return (must_create, version)
fn schema_version(db: &Connection) -> Result<(bool, u8)> {
    if !db
        .prepare("select null from sqlite_master where type = 'table' and name = 'col'")?
        .exists(NO_PARAMS)?
    {
        return Ok((true, SCHEMA_STARTING_VERSION));
    }

    Ok((
        false,
        db.query_row("select ver from col", NO_PARAMS, |r| Ok(r.get(0)?))?,
    ))
}

fn trace(s: &str) {
    println!("sql: {}", s.trim().replace('\n', " "));
}

impl SqliteStorage {
    pub(crate) fn open_or_create(path: &Path, i18n: &I18n, server: bool) -> Result<Self> {
        let db = open_or_create_collection_db(path)?;
        let (create, ver) = schema_version(&db)?;

        let err = match ver {
            v if v < SCHEMA_MIN_VERSION => Some(DBErrorKind::FileTooOld),
            v if v > SCHEMA_MAX_VERSION => Some(DBErrorKind::FileTooNew),
            12 | 13 => {
                // as schema definition changed, user must perform clean
                // shutdown to return to schema 11 prior to running this version
                Some(DBErrorKind::FileTooNew)
            }
            _ => None,
        };
        if let Some(kind) = err {
            return Err(AnkiError::DBError {
                info: "".to_string(),
                kind,
            });
        }

        let upgrade = ver != SCHEMA_MAX_VERSION;
        if create || upgrade {
            db.execute("begin exclusive", NO_PARAMS)?;
        }

        if create {
            db.execute_batch(include_str!("schema11.sql"))?;
            // start at schema 11, then upgrade below
            let crt = v1_creation_date();
            db.execute(
                "update col set crt=?, scm=?, ver=?, conf=?",
                params![
                    crt,
                    TimestampMillis::now(),
                    SCHEMA_STARTING_VERSION,
                    &schema11_config_as_string()
                ],
            )?;
        }

        let storage = Self { db };

        if create || upgrade {
            storage.upgrade_to_latest_schema(ver, server)?;
        }

        if create {
            storage.add_default_deck_config(i18n)?;
            storage.add_default_deck(i18n)?;
            storage.add_stock_notetypes(i18n)?;
        }

        if create || upgrade {
            storage.commit_trx()?;
        }

        Ok(storage)
    }

    pub(crate) fn close(self, downgrade: bool) -> Result<()> {
        if downgrade {
            self.downgrade_to_schema_11()?;
            self.db.pragma_update(None, "journal_mode", &"delete")?;
        }
        Ok(())
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

    pub(crate) fn rollback_rust_trx(&self) -> Result<()> {
        self.db
            .prepare_cached("rollback to rust")?
            .execute(NO_PARAMS)?;
        Ok(())
    }

    //////////////////////////////////////////

    pub(crate) fn mark_modified(&self) -> Result<()> {
        self.set_modified_time(TimestampMillis::now())
    }

    pub(crate) fn set_modified_time(&self, stamp: TimestampMillis) -> Result<()> {
        self.db
            .prepare_cached("update col set mod=?")?
            .execute(params![stamp])?;
        Ok(())
    }

    pub(crate) fn get_modified_time(&self) -> Result<TimestampMillis> {
        self.db
            .prepare_cached("select mod from col")?
            .query_and_then(NO_PARAMS, |r| r.get(0))?
            .next()
            .ok_or_else(|| AnkiError::invalid_input("missing col"))?
            .map_err(Into::into)
    }

    pub(crate) fn creation_stamp(&self) -> Result<TimestampSecs> {
        self.db
            .prepare_cached("select crt from col")?
            .query_row(NO_PARAMS, |row| row.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn set_creation_stamp(&self, stamp: TimestampSecs) -> Result<()> {
        self.db
            .prepare("update col set crt = ?")?
            .execute(&[stamp])?;
        Ok(())
    }

    pub(crate) fn set_schema_modified(&self) -> Result<()> {
        self.db
            .prepare_cached("update col set scm = ?")?
            .execute(&[TimestampMillis::now()])?;
        Ok(())
    }

    pub(crate) fn get_schema_mtime(&self) -> Result<TimestampMillis> {
        self.db
            .prepare_cached("select scm from col")?
            .query_and_then(NO_PARAMS, |r| r.get(0))?
            .next()
            .ok_or_else(|| AnkiError::invalid_input("missing col"))?
            .map_err(Into::into)
    }

    pub(crate) fn get_last_sync(&self) -> Result<TimestampMillis> {
        self.db
            .prepare_cached("select ls from col")?
            .query_and_then(NO_PARAMS, |r| r.get(0))?
            .next()
            .ok_or_else(|| AnkiError::invalid_input("missing col"))?
            .map_err(Into::into)
    }

    pub(crate) fn set_last_sync(&self, stamp: TimestampMillis) -> Result<()> {
        self.db
            .prepare("update col set ls = ?")?
            .execute(&[stamp])?;
        Ok(())
    }

    //////////////////////////////////////////

    /// true if corrupt/can't access
    pub(crate) fn quick_check_corrupt(&self) -> bool {
        match self.db.pragma_query_value(None, "quick_check", |row| {
            row.get(0).map(|v: String| v != "ok")
        }) {
            Ok(corrupt) => corrupt,
            Err(e) => {
                println!("error: {:?}", e);
                true
            }
        }
    }

    pub(crate) fn optimize(&self) -> Result<()> {
        self.db.execute_batch("vacuum; reindex; analyze")?;
        Ok(())
    }

    #[cfg(test)]
    pub(crate) fn db_scalar<T: rusqlite::types::FromSql>(&self, sql: &str) -> Result<T> {
        self.db
            .query_row(sql, NO_PARAMS, |r| r.get(0))
            .map_err(Into::into)
    }
}
