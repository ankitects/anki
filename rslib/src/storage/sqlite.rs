// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{borrow::Cow, cmp::Ordering, hash::Hasher, path::Path, sync::Arc};

use fnv::FnvHasher;
use regex::Regex;
use rusqlite::{functions::FunctionFlags, params, Connection};
use unicase::UniCase;

use super::upgrades::{SCHEMA_MAX_VERSION, SCHEMA_MIN_VERSION, SCHEMA_STARTING_VERSION};
use crate::{
    config::schema11::schema11_config_as_string,
    error::{AnkiError, DbErrorKind, Result},
    i18n::I18n,
    scheduler::timing::{local_minutes_west_for_stamp, v1_creation_date},
    text::without_combining,
    timestamp::TimestampMillis,
};

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
    add_fnvhash_function(&db)?;

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

fn add_fnvhash_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function("fnvhash", -1, FunctionFlags::SQLITE_DETERMINISTIC, |ctx| {
        let mut hasher = FnvHasher::default();
        for idx in 0..ctx.len() {
            hasher.write_i64(ctx.get(idx)?);
        }
        Ok(hasher.finish() as i64)
    })
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
        .exists([])?
    {
        return Ok((true, SCHEMA_STARTING_VERSION));
    }

    Ok((
        false,
        db.query_row("select ver from col", [], |r| r.get(0).map_err(Into::into))?,
    ))
}

fn trace(s: &str) {
    println!("sql: {}", s.trim().replace('\n', " "));
}

impl SqliteStorage {
    pub(crate) fn open_or_create(path: &Path, tr: &I18n, server: bool) -> Result<Self> {
        let db = open_or_create_collection_db(path)?;
        let (create, ver) = schema_version(&db)?;

        let err = match ver {
            v if v < SCHEMA_MIN_VERSION => Some(DbErrorKind::FileTooOld),
            v if v > SCHEMA_MAX_VERSION => Some(DbErrorKind::FileTooNew),
            12 | 13 => {
                // as schema definition changed, user must perform clean
                // shutdown to return to schema 11 prior to running this version
                Some(DbErrorKind::FileTooNew)
            }
            _ => None,
        };
        if let Some(kind) = err {
            return Err(AnkiError::db_error("", kind));
        }

        let upgrade = ver != SCHEMA_MAX_VERSION;
        if create || upgrade {
            db.execute("begin exclusive", [])?;
        }

        if create {
            db.execute_batch(include_str!("schema11.sql"))?;
            // start at schema 11, then upgrade below
            let crt = v1_creation_date();
            let offset = if server {
                None
            } else {
                Some(local_minutes_west_for_stamp(crt))
            };
            db.execute(
                "update col set crt=?, scm=?, ver=?, conf=?",
                params![
                    crt,
                    TimestampMillis::now(),
                    SCHEMA_STARTING_VERSION,
                    &schema11_config_as_string(offset)
                ],
            )?;
        }

        let storage = Self { db };

        if create || upgrade {
            storage.upgrade_to_latest_schema(ver, server)?;
        }

        if create {
            storage.add_default_deck_config(tr)?;
            storage.add_default_deck(tr)?;
            storage.add_stock_notetypes(tr)?;
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
        self.db.prepare_cached("begin exclusive")?.execute([])?;
        Ok(())
    }

    pub(crate) fn commit_trx(&self) -> Result<()> {
        if !self.db.is_autocommit() {
            self.db.prepare_cached("commit")?.execute([])?;
        }
        Ok(())
    }

    pub(crate) fn rollback_trx(&self) -> Result<()> {
        if !self.db.is_autocommit() {
            self.db.execute("rollback", [])?;
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
        self.db.prepare_cached("savepoint rust")?.execute([])?;
        Ok(())
    }

    pub(crate) fn commit_rust_trx(&self) -> Result<()> {
        self.db.prepare_cached("release rust")?.execute([])?;
        Ok(())
    }

    pub(crate) fn rollback_rust_trx(&self) -> Result<()> {
        self.db.prepare_cached("rollback to rust")?.execute([])?;
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
        self.db.query_row(sql, [], |r| r.get(0)).map_err(Into::into)
    }
}
