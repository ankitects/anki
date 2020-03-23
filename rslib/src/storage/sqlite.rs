// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::collection::CollectionOp;
use crate::config::Config;
use crate::err::Result;
use crate::err::{AnkiError, DBErrorKind};
use crate::time::{i64_unix_millis, i64_unix_secs};
use crate::{
    decks::Deck,
    notetypes::NoteType,
    sched::cutoff::{sched_timing_today, SchedTimingToday},
    text::without_combining,
    types::{ObjID, Usn},
};
use regex::Regex;
use rusqlite::{params, Connection, NO_PARAMS};
use std::cmp::Ordering;
use std::{
    borrow::Cow,
    collections::HashMap,
    path::{Path, PathBuf},
};
use unicase::UniCase;

const SCHEMA_MIN_VERSION: u8 = 11;
const SCHEMA_MAX_VERSION: u8 = 11;

fn unicase_compare(s1: &str, s2: &str) -> Ordering {
    UniCase::new(s1).cmp(&UniCase::new(s2))
}

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
    db.pragma_update(None, "journal_mode", &"wal")?;
    db.pragma_update(None, "temp_store", &"memory")?;

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
    db.create_scalar_function("field_at_index", 2, true, |ctx| {
        let mut fields = ctx.get_raw(0).as_str()?.split('\x1f');
        let idx: u16 = ctx.get(1)?;
        Ok(fields.nth(idx as usize).unwrap_or("").to_string())
    })
}

fn add_without_combining_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function("without_combining", 1, true, |ctx| {
        let text = ctx.get_raw(0).as_str()?;
        Ok(match without_combining(text) {
            Cow::Borrowed(_) => None,
            Cow::Owned(o) => Some(o),
        })
    })
}

/// Adds sql function regexp(regex, string) -> is_match
/// Taken from the rusqlite docs
fn add_regexp_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function("regexp", 2, true, move |ctx| {
        assert_eq!(ctx.len(), 2, "called with unexpected number of arguments");

        let saved_re: Option<&Regex> = ctx.get_aux(0)?;
        let new_re = match saved_re {
            None => {
                let s = ctx.get::<String>(0)?;
                match Regex::new(&s) {
                    Ok(r) => Some(r),
                    Err(err) => return Err(rusqlite::Error::UserFunctionError(Box::new(err))),
                }
            }
            Some(_) => None,
        };

        let is_match = {
            let re = saved_re.unwrap_or_else(|| new_re.as_ref().unwrap());

            let text = ctx
                .get_raw(1)
                .as_str()
                .map_err(|e| rusqlite::Error::UserFunctionError(e.into()))?;

            re.is_match(text)
        };

        if let Some(re) = new_re {
            ctx.set_aux(0, re);
        }

        Ok(is_match)
    })
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

    timing_today: Option<SchedTimingToday>,
}

impl StorageContext<'_> {
    fn new(db: &Connection, server: bool) -> StorageContext {
        StorageContext {
            db,
            server,
            usn: None,
            timing_today: None,
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

    pub(crate) fn all_decks(&self) -> Result<HashMap<ObjID, Deck>> {
        self.db
            .query_row_and_then("select decks from col", NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })
    }

    pub(crate) fn all_config(&self) -> Result<Config> {
        self.db
            .query_row_and_then("select conf from col", NO_PARAMS, |row| -> Result<_> {
                Ok(serde_json::from_str(row.get_raw(0).as_str()?)?)
            })
    }

    pub(crate) fn all_note_types(&self) -> Result<HashMap<ObjID, NoteType>> {
        let mut stmt = self.db.prepare("select models from col")?;
        let note_types = stmt
            .query_and_then(NO_PARAMS, |row| -> Result<HashMap<ObjID, NoteType>> {
                let v: HashMap<ObjID, NoteType> = serde_json::from_str(row.get_raw(0).as_str()?)?;
                Ok(v)
            })?
            .next()
            .ok_or_else(|| AnkiError::DBError {
                info: "col table empty".to_string(),
                kind: DBErrorKind::MissingEntity,
            })??;
        Ok(note_types)
    }

    #[allow(dead_code)]
    pub(crate) fn timing_today(&mut self) -> Result<SchedTimingToday> {
        if self.timing_today.is_none() {
            let crt: i64 = self
                .db
                .prepare_cached("select crt from col")?
                .query_row(NO_PARAMS, |row| row.get(0))?;
            let conf = self.all_config()?;
            let now_offset = if self.server { conf.local_offset } else { None };

            self.timing_today = Some(sched_timing_today(
                crt,
                i64_unix_secs(),
                conf.creation_offset,
                now_offset,
                conf.rollover,
            ));
        }
        Ok(*self.timing_today.as_ref().unwrap())
    }
}
