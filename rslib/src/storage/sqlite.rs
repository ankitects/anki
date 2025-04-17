// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::cmp::Ordering;
use std::collections::HashSet;
use std::fmt::Display;
use std::hash::Hasher;
use std::path::Path;
use std::sync::Arc;

use fnv::FnvHasher;
use fsrs::FSRS;
use regex::Regex;
use rusqlite::functions::FunctionFlags;
use rusqlite::params;
use rusqlite::Connection;
use serde_json::Value;
use unicase::UniCase;

use super::upgrades::SCHEMA_MAX_VERSION;
use super::upgrades::SCHEMA_MIN_VERSION;
use super::upgrades::SCHEMA_STARTING_VERSION;
use super::SchemaVersion;
use crate::config::schema11::schema11_config_as_string;
use crate::error::DbErrorKind;
use crate::prelude::*;
use crate::scheduler::timing::local_minutes_west_for_stamp;
use crate::scheduler::timing::v1_creation_date;
use crate::storage::card::data::CardData;
use crate::text::without_combining;

fn unicase_compare(s1: &str, s2: &str) -> Ordering {
    UniCase::new(s1).cmp(&UniCase::new(s2))
}

// fixme: rollback savepoint when tags not changed
// fixme: need to drop out of wal prior to vacuuming to fix page size of older
// collections

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

    db.pragma_update(None, "locking_mode", "exclusive")?;
    db.pragma_update(None, "page_size", 4096)?;
    db.pragma_update(None, "cache_size", -40 * 1024)?;
    db.pragma_update(None, "legacy_file_format", false)?;
    db.pragma_update(None, "journal_mode", "wal")?;
    // Android has no /tmp folder, and fails in the default config.
    #[cfg(target_os = "android")]
    db.pragma_update(None, "temp_store", &"memory")?;

    db.set_prepared_statement_cache_capacity(50);

    add_field_index_function(&db)?;
    add_regexp_function(&db)?;
    add_regexp_fields_function(&db)?;
    add_regexp_tags_function(&db)?;
    add_without_combining_function(&db)?;
    add_fnvhash_function(&db)?;
    add_extract_original_position_function(&db)?;
    add_extract_custom_data_function(&db)?;
    add_extract_fsrs_variable(&db)?;
    add_extract_fsrs_retrievability(&db)?;
    add_extract_fsrs_relative_retrievability(&db)?;

    db.create_collation("unicase", unicase_compare)?;

    Ok(db)
}

impl SqliteStorage {
    /// This is provided as an escape hatch for when you need to do something
    /// not directly supported by this library. Please exercise caution when
    /// using it.
    pub fn db(&self) -> &Connection {
        &self.db
    }
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

/// Adds sql function `regexp_fields(regex, note_flds, indices...) -> is_match`.
/// If no indices are provided, all fields are matched against.
fn add_regexp_fields_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "regexp_fields",
        -1,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert!(ctx.len() > 1, "not enough arguments");

            let re: Arc<Regex> = ctx
                .get_or_create_aux(0, |vr| -> std::result::Result<_, BoxError> {
                    Ok(Regex::new(vr.as_str()?)?)
                })?;
            let fields = ctx.get_raw(1).as_str()?.split('\x1f');
            let indices: HashSet<usize> = (2..ctx.len())
                .map(|i| ctx.get(i))
                .collect::<rusqlite::Result<_>>()?;

            Ok(fields.enumerate().any(|(idx, field)| {
                (indices.is_empty() || indices.contains(&idx)) && re.is_match(field)
            }))
        },
    )
}

/// Adds sql function `regexp_tags(regex, tags) -> is_match`.
fn add_regexp_tags_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "regexp_tags",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 2, "called with unexpected number of arguments");

            let re: Arc<Regex> = ctx
                .get_or_create_aux(0, |vr| -> std::result::Result<_, BoxError> {
                    Ok(Regex::new(vr.as_str()?)?)
                })?;
            let mut tags = ctx.get_raw(1).as_str()?.split(' ');

            Ok(tags.any(|tag| re.is_match(tag)))
        },
    )
}

/// eg. extract_original_position(c.data) -> number | null
/// Parse original card position from c.data (this is only populated after card
/// has been reviewed)
fn add_extract_original_position_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_original_position",
        1,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 1, "called with unexpected number of arguments");

            let Ok(card_data) = ctx.get_raw(0).as_str() else {
                return Ok(None);
            };

            match &CardData::from_str(card_data).original_position {
                Some(position) => Ok(Some(*position as i64)),
                None => Ok(None),
            }
        },
    )
}

/// eg. extract_custom_data(card.data, 'r') -> string | null
fn add_extract_custom_data_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_custom_data",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 2, "called with unexpected number of arguments");

            let Ok(card_data) = ctx.get_raw(0).as_str() else {
                return Ok(None);
            };
            if card_data.is_empty() {
                return Ok(None);
            }
            let Ok(key) = ctx.get_raw(1).as_str() else {
                return Ok(None);
            };
            let custom_data = &CardData::from_str(card_data).custom_data;
            let Ok(value) = serde_json::from_str::<Value>(custom_data) else {
                return Ok(None);
            };
            let v = value.get(key).map(|v| match v {
                Value::String(s) => s.to_owned(),
                _ => v.to_string(),
            });
            Ok(v)
        },
    )
}

/// eg. extract_fsrs_variable(card.data, 's' | 'd' | 'dr') -> float | null
fn add_extract_fsrs_variable(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_fsrs_variable",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 2, "called with unexpected number of arguments");

            let Ok(card_data) = ctx.get_raw(0).as_str() else {
                return Ok(None);
            };
            if card_data.is_empty() {
                return Ok(None);
            }
            let Ok(key) = ctx.get_raw(1).as_str() else {
                return Ok(None);
            };
            let card_data = &CardData::from_str(card_data);
            Ok(match key {
                "s" => card_data.fsrs_stability,
                "d" => card_data.fsrs_difficulty,
                "dr" => card_data.fsrs_desired_retention,
                _ => panic!("invalid key: {key}"),
            })
        },
    )
}

/// eg. extract_fsrs_retrievability(card.data, card.due, card.ivl,
/// timing.days_elapsed, timing.next_day_at) -> float | null
fn add_extract_fsrs_retrievability(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_fsrs_retrievability",
        5,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 5, "called with unexpected number of arguments");
            let Ok(card_data) = ctx.get_raw(0).as_str() else {
                return Ok(None);
            };
            if card_data.is_empty() {
                return Ok(None);
            }
            let card_data = &CardData::from_str(card_data);
            let Ok(due) = ctx.get_raw(1).as_i64() else {
                return Ok(None);
            };
            let days_elapsed = if due > 365_000 {
                // (re)learning card in seconds
                let Ok(next_day_at) = ctx.get_raw(4).as_i64() else {
                    return Ok(None);
                };
                (next_day_at).saturating_sub(due) as u32 / 86_400
            } else {
                let Ok(ivl) = ctx.get_raw(2).as_i64() else {
                    return Ok(None);
                };
                let Ok(days_elapsed) = ctx.get_raw(3).as_i64() else {
                    return Ok(None);
                };
                let review_day = due.saturating_sub(ivl);
                days_elapsed.saturating_sub(review_day) as u32
            };
            let decay = card_data.decay.unwrap_or(0.5);
            Ok(card_data.memory_state().map(|state| {
                FSRS::new(None)
                    .unwrap()
                    .current_retrievability(state.into(), days_elapsed, decay)
            }))
        },
    )
}

/// eg. extract_fsrs_relative_retrievability(card.data, card.due,
/// timing.days_elapsed, card.ivl, timing.next_day_at) -> float | null. The
/// higher the number, the higher the card's retrievability relative to the
/// configured desired retention.
fn add_extract_fsrs_relative_retrievability(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_fsrs_relative_retrievability",
        5,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 5, "called with unexpected number of arguments");

            let Ok(due) = ctx.get_raw(1).as_i64() else {
                return Ok(None);
            };
            let Ok(interval) = ctx.get_raw(3).as_i64() else {
                return Ok(None);
            };
            let Ok(next_day_at) = ctx.get_raw(4).as_i64() else {
                return Ok(None);
            };
            let days_elapsed = if due > 365_000 {
                // (re)learning
                next_day_at.saturating_sub(due) as u32 / 86_400
            } else {
                let Ok(days_elapsed) = ctx.get_raw(2).as_i64() else {
                    return Ok(None);
                };
                let review_day = due.saturating_sub(interval);

                days_elapsed.saturating_sub(review_day) as u32
            };
            if let Ok(card_data) = ctx.get_raw(0).as_str() {
                if !card_data.is_empty() {
                    let card_data = &CardData::from_str(card_data);
                    if let (Some(state), Some(mut desired_retrievability)) =
                        (card_data.memory_state(), card_data.fsrs_desired_retention)
                    {
                        // avoid div by zero
                        desired_retrievability = desired_retrievability.max(0.0001);
                        let decay = card_data.decay.unwrap_or(0.5);

                        let current_retrievability = FSRS::new(None)
                            .unwrap()
                            .current_retrievability(state.into(), days_elapsed, decay)
                            .max(0.0001);

                        return Ok(Some(
                            // power should be the reciprocal of the value of DECAY in FSRS-rs,
                            // which is currently -0.5
                            -(current_retrievability.powi(-2) - 1.)
                                / (desired_retrievability.powi(-2) - 1.),
                        ));
                    }
                }
            }

            // FSRS data missing; fall back to SM2 ordering
            Ok(Some(
                -((days_elapsed as f32) + 0.001) / (interval as f32).max(1.0),
            ))
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
        db.query_row("select ver from col", [], |r| r.get(0))?,
    ))
}

fn trace(s: &str) {
    println!("sql: {}", s.trim().replace('\n', " "));
}

impl SqliteStorage {
    pub(crate) fn open_or_create(
        path: &Path,
        tr: &I18n,
        server: bool,
        check_integrity: bool,
    ) -> Result<Self> {
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

        if check_integrity {
            match db.pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0)) {
                Ok(s) => require!(s == "ok", "corrupt: {s}"),
                Err(e) => return Err(e.into()),
            };
        }

        let upgrade = ver != SCHEMA_MAX_VERSION;
        if create || upgrade {
            db.execute("begin exclusive", [])?;
        }

        if create {
            db.execute_batch(include_str!("schema11.sql"))?;
            // start at schema 11, then upgrade below
            let crt = TimestampSecs(v1_creation_date());
            let offset = if server {
                None
            } else {
                Some(local_minutes_west_for_stamp(crt)?)
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

    pub(crate) fn close(self, desired_version: Option<SchemaVersion>) -> Result<()> {
        if let Some(version) = desired_version {
            self.downgrade_to(version)?;
            if version.has_journal_mode_delete() {
                self.db.pragma_update(None, "journal_mode", "delete")?;
            }
        }
        Ok(())
    }

    /// Flush data from WAL file into DB, so the DB is safe to copy. Caller must
    /// not call this while there is an active transaction.
    pub(crate) fn checkpoint(&self) -> Result<()> {
        if !self.db.is_autocommit() {
            return Err(AnkiError::db_error(
                "active transaction",
                DbErrorKind::Other,
            ));
        }
        self.db
            .query_row_and_then("pragma wal_checkpoint(truncate)", [], |row| {
                let error_code: i64 = row.get(0)?;
                if error_code != 0 {
                    Err(AnkiError::db_error(
                        "unable to checkpoint",
                        DbErrorKind::Other,
                    ))
                } else {
                    Ok(())
                }
            })
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

#[derive(Debug, Clone, Copy)]
pub enum SqlSortOrder {
    Ascending,
    Descending,
}

impl Display for SqlSortOrder {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "{}",
            match self {
                SqlSortOrder::Ascending => "asc",
                SqlSortOrder::Descending => "desc",
            }
        )
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::scheduler::answering::test::v3_test_collection;
    use crate::storage::card::ReviewOrderSubclause;

    #[test]
    fn missing_memory_state_falls_back_to_sm2() -> Result<()> {
        let (mut col, _cids) = v3_test_collection(1)?;
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        col.answer_easy();

        let timing = col.timing_today()?;
        let order = SqlSortOrder::Ascending;
        let sql_func = ReviewOrderSubclause::RetrievabilityFsrs { timing, order }
            .to_string()
            .replace(" asc", "");
        let sql = format!("select {sql_func} from cards");

        // value from fsrs
        let mut pos: Option<f64>;
        pos = col.storage.db_scalar(&sql).unwrap();
        assert_eq!(pos, Some(0.0));
        // erasing the memory state should not result in None output
        col.storage.db.execute("update cards set data=''", [])?;
        pos = col.storage.db_scalar(&sql).unwrap();
        assert!(pos.is_some());
        // but it won't match the fsrs value
        assert!(pos.unwrap() < -0.0);
        Ok(())
    }
}
