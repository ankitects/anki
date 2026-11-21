// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::cmp::Ordering;
use std::fmt::Display;
use std::hash::Hasher;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

use bitflags::bitflags;
use fnv::FnvHasher;
use fsrs::current_retrievability;
use fsrs::FSRS5_DEFAULT_DECAY;
use regex::Regex;
use rusqlite::functions::FunctionFlags;
use rusqlite::params;
use rusqlite::trace::TraceEvent;
use rusqlite::types::FromSqlError;
use rusqlite::types::ValueRef;
use rusqlite::Connection;
use serde_json::Value;
use unicase::UniCase;

use super::upgrades::SCHEMA_MAX_VERSION;
use super::upgrades::SCHEMA_MIN_VERSION;
use super::upgrades::SCHEMA_STARTING_VERSION;
use super::SchemaVersion;
use crate::cloze::strip_clozes;
use crate::config::schema11::schema11_config_as_string;
use crate::error::DbErrorKind;
use crate::prelude::*;
use crate::scheduler::timing::local_minutes_west_for_stamp;
use crate::scheduler::timing::v1_creation_date;
use crate::storage::card::data::CardData;
use crate::text::without_combining;
use crate::text::CowMapping;

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

pub(crate) const RETRIEVABILITY_CACHE_DB_SCHEMA: &str = "retrievability_cache";

fn open_or_create_collection_db(
    path: &Path,
    persistent_retrievability_cache: bool,
) -> Result<Connection> {
    let mut db = Connection::open(path)?;

    if std::env::var("TRACESQL").is_ok() {
        db.trace_v2(
            rusqlite::trace::TraceEventCodes::SQLITE_TRACE_STMT,
            Some(trace),
        )?;
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
    add_process_text_function(&db)?;
    add_fnvhash_function(&db)?;
    add_extract_original_position_function(&db)?;
    add_extract_custom_data_function(&db)?;
    add_extract_fsrs_variable(&db)?;
    add_extract_fsrs_retrievability(&db)?;
    add_extract_fsrs_relative_retrievability(&db)?;

    db.create_collation("unicase", unicase_compare)?;

    attach_retrievability_cache_db(&db, persistent_retrievability_cache.then_some(path))?;

    Ok(db)
}

pub(crate) fn retrievability_cache_path(collection_path: &Path) -> PathBuf {
    if collection_path == Path::new(":memory:") {
        PathBuf::from(":memory:")
    } else {
        collection_path
            .parent()
            .unwrap_or_else(|| Path::new(""))
            .join("rwkv-state-cache")
            .join(
                collection_path
                    .with_extension("retrievability-cache.sqlite")
                    .file_name()
                    .unwrap(),
            )
    }
}

fn relocate_retrievability_cache(collection_path: &Path, cache_path: &Path) -> Result<()> {
    if cache_path == Path::new(":memory:") {
        return Ok(());
    }
    if let Err(err) = anki_io::create_dir_all(cache_path.parent().unwrap()) {
        tracing::warn!(?err, "failed to prepare retrievability cache directory");
        return Ok(());
    }
    let old_path = collection_path.with_extension("retrievability-cache.sqlite");
    if old_path.exists() && !cache_path.exists() {
        // Checkpoint before moving: committed predictions may still be in the WAL.
        // Switching journal mode also refuses relocation while another reader is busy.
        let mode = (|| -> rusqlite::Result<String> {
            let old_db = Connection::open(&old_path)?;
            old_db.query_row("PRAGMA journal_mode = DELETE", [], |row| row.get(0))
        })();
        match mode {
            Ok(mode) => require!(mode == "delete", "retrievability cache is busy"),
            Err(err) if retrievability_cache_is_busy(&err) => return Err(err.into()),
            Err(err) => {
                tracing::warn!(
                    ?err,
                    "failed to open old retrievability cache for relocation"
                );
                return Ok(());
            }
        }
        if let Err(err) = std::fs::rename(&old_path, cache_path) {
            tracing::warn!(?err, "failed to relocate retrievability cache");
        }
    }
    Ok(())
}

fn retrievability_cache_is_busy(err: &rusqlite::Error) -> bool {
    matches!(
        err.sqlite_error_code(),
        Some(rusqlite::ErrorCode::DatabaseBusy | rusqlite::ErrorCode::DatabaseLocked)
    )
}

fn attach_retrievability_cache_db(db: &Connection, collection_path: Option<&Path>) -> Result<()> {
    let cache_path = collection_path
        .map(retrievability_cache_path)
        .unwrap_or_else(|| PathBuf::from(":memory:"));
    if let Some(collection_path) = collection_path {
        relocate_retrievability_cache(collection_path, &cache_path)?;
    }
    let attach = || -> rusqlite::Result<()> {
        db.execute(
            &format!("ATTACH DATABASE ? AS {RETRIEVABILITY_CACHE_DB_SCHEMA}"),
            [cache_path.to_string_lossy().as_ref()],
        )?;
        db.pragma_update(Some(RETRIEVABILITY_CACHE_DB_SCHEMA), "journal_mode", "wal")
    };
    if let Err(err) = attach() {
        if retrievability_cache_is_busy(&err) || !cache_path.is_file() {
            return Err(err.into());
        }
        tracing::warn!(?err, "recreating unusable retrievability cache");
        let _ = db.execute(
            &format!("DETACH DATABASE {RETRIEVABILITY_CACHE_DB_SCHEMA}"),
            [],
        );
        for path in [
            cache_path.clone(),
            PathBuf::from(format!("{}-wal", cache_path.display())),
            PathBuf::from(format!("{}-shm", cache_path.display())),
        ] {
            match std::fs::remove_file(path) {
                Ok(()) => {}
                Err(err) if err.kind() == std::io::ErrorKind::NotFound => {}
                Err(err) => return Err(err.into()),
            }
        }
        attach()?;
    }
    Ok(())
}

impl SqliteStorage {
    /// Validate an extracted backup without creating sidecars or upgrading it.
    pub(crate) fn check_backup_file(path: &Path) -> Result<()> {
        let mut uri = reqwest::Url::from_file_path(path)
            .ok()
            .or_invalid("invalid backup path")?;
        uri.query_pairs_mut()
            .append_pair("mode", "ro")
            .append_pair("immutable", "1");
        let db = Connection::open_with_flags(
            uri.as_str(),
            rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_URI,
        )?;
        db.create_collation("unicase", unicase_compare)?;
        let (create, version) = schema_version(&db)?;
        require!(
            !create
                && (SCHEMA_MIN_VERSION..=SCHEMA_MAX_VERSION).contains(&version)
                && !matches!(version, 12 | 13),
            "invalid backup collection schema"
        );
        for table in ["cards", "notes", "revlog"] {
            db.prepare(&format!("select id from {table}"))?;
        }
        let result =
            db.pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0))?;
        require!(result == "ok", "corrupt backup: {result}");
        Ok(())
    }

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

bitflags! {
    pub(crate) struct ProcessTextFlags: u8 {
        const NoCombining = 1;
        const StripClozes = 1 << 1;
    }
}

fn add_process_text_function(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "process_text",
        2,
        FunctionFlags::SQLITE_DETERMINISTIC,
        |ctx| {
            let mut text = Cow::from(ctx.get_raw(0).as_str()?);
            let opt = ProcessTextFlags::from_bits_truncate(ctx.get_raw(1).as_i64()? as u8);
            if opt.contains(ProcessTextFlags::StripClozes) {
                text = text.map_cow(strip_clozes);
            }
            if opt.contains(ProcessTextFlags::NoCombining) {
                text = text.map_cow(without_combining);
            }
            Ok(text.get_owned())
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
            if ctx.len() == 2 {
                let fields = ctx.get_raw(1).as_str()?;
                return Ok(fields.split('\x1f').any(|field| re.is_match(field)));
            }

            let ValueRef::Text(fields) = ctx.get_raw(1) else {
                return Err(FromSqlError::InvalidType.into());
            };
            let indices = (2..ctx.len())
                .map(|arg_idx| ctx.get::<usize>(arg_idx))
                .collect::<rusqlite::Result<Vec<_>>>()?;
            let last_index = *indices.iter().max().unwrap();
            // Split the borrowed bytes before validating UTF-8, so matching
            // Front does not scan large HTML fields later in the note.
            for (idx, field) in fields
                .split(|&byte| byte == b'\x1f')
                .take(last_index.saturating_add(1))
                .enumerate()
            {
                if indices.contains(&idx) && re.is_match(ValueRef::Text(field).as_str()?) {
                    return Ok(true);
                }
            }
            Ok(false)
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

/// eg. extract_fsrs_variable(card.data, 's' | 's_int' | 's_fast' | 'd' |
/// 'dr') -> float | null
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
                "s_int" => card_data.fsrs_stability_internal,
                "s_fast" => card_data.fsrs_stability_fast,
                "d" => card_data.fsrs_difficulty,
                "dr" => card_data.fsrs_desired_retention,
                _ => panic!("invalid key: {key}"),
            })
        },
    )
}

/// eg. extract_fsrs_retrievability(card.data, card.due, card.ivl,
/// timing.days_elapsed, timing.next_day_at, timing.now) -> float | null
fn add_extract_fsrs_retrievability(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_fsrs_retrievability",
        6,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 6, "called with unexpected number of arguments");
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
            let Ok(now) = ctx.get_raw(5).as_i64() else {
                return Ok(None);
            };
            let seconds_elapsed = if let Some(last_review_time) = card_data.last_review_time {
                // This and any following
                // (x as u32).saturating_sub(y as u32)
                // must not be changed to
                // x.saturating_sub(y) as u32
                // as x and y are i64's and saturating_sub will therfore allow negative numbers
                // before converting to u32 in the latter example.
                (now as u32).saturating_sub(last_review_time.0 as u32)
            } else if due > 365_000 {
                // (re)learning card in seconds
                let Ok(ivl) = ctx.get_raw(2).as_i64() else {
                    return Ok(None);
                };
                let last_review_time = (due as u32).saturating_sub(ivl as u32);
                (now as u32).saturating_sub(last_review_time)
            } else {
                let Ok(ivl) = ctx.get_raw(2).as_i64() else {
                    return Ok(None);
                };
                // timing.days_elapsed
                let Ok(today) = ctx.get_raw(3).as_i64() else {
                    return Ok(None);
                };
                let review_day = (due as u32).saturating_sub(ivl as u32);
                (today as u32).saturating_sub(review_day) * 86_400
            };
            let decay = card_data.decay.unwrap_or(FSRS5_DEFAULT_DECAY);
            let retrievability = card_data.memory_state().map(|state| {
                current_retrievability(state.into(), seconds_elapsed as f32 / 86_400.0, decay)
            });
            Ok(retrievability)
        },
    )
}

/// eg. extract_fsrs_relative_retrievability(card.data, card.due,
/// card.ivl, timing.days_elapsed, timing.next_day_at, timing.now) -> float |
/// null. The higher the number, the higher the card's retrievability relative
/// to the configured desired retention.
fn add_extract_fsrs_relative_retrievability(db: &Connection) -> rusqlite::Result<()> {
    db.create_scalar_function(
        "extract_fsrs_relative_retrievability",
        6,
        FunctionFlags::SQLITE_DETERMINISTIC,
        move |ctx| {
            assert_eq!(ctx.len(), 6, "called with unexpected number of arguments");

            let Ok(due) = ctx.get_raw(1).as_i64() else {
                return Ok(None);
            };
            let Ok(interval) = ctx.get_raw(2).as_i64() else {
                return Ok(None);
            };
            /*
            // Unused
            let Ok(next_day_at) = ctx.get_raw(4).as_i64() else {
                return Ok(None);
            };
            */
            let Ok(now) = ctx.get_raw(5).as_i64() else {
                return Ok(None);
            };
            let secs_elapsed = if due > 365_000 {
                // (re)learning card with due in seconds

                // Don't change this to now.subtracting_sub(due) as u32
                // for the same reasons listed in the comment
                // in add_extract_fsrs_retrievability
                (now as u32).saturating_sub(due as u32)
            } else {
                // timing.days_elapsed
                let Ok(today) = ctx.get_raw(3).as_i64() else {
                    return Ok(None);
                };
                let review_day = due.saturating_sub(interval);
                (today as u32).saturating_sub(review_day as u32) * 86_400
            };
            if let Ok(card_data) = ctx.get_raw(0).as_str() {
                if !card_data.is_empty() {
                    let card_data = &CardData::from_str(card_data);
                    if let (Some(state), Some(mut desired_retrievability)) =
                        (card_data.memory_state(), card_data.fsrs_desired_retention)
                    {
                        // avoid div by zero
                        desired_retrievability = desired_retrievability.max(0.0001);
                        let decay = card_data.decay.unwrap_or(FSRS5_DEFAULT_DECAY);

                        let seconds_elapsed =
                            if let Some(last_review_time) = card_data.last_review_time {
                                // Don't change this to now.subtracting_sub(due) as u32
                                // for the same reasons listed in the comment
                                // in add_extract_fsrs_retrievability
                                (now as u32).saturating_sub(last_review_time.0 as u32)
                            } else {
                                secs_elapsed
                            };

                        let current_retrievability = current_retrievability(
                            state.into(),
                            seconds_elapsed as f32 / 86_400.0,
                            decay,
                        )
                        .max(0.0001);

                        return Ok(Some(
                            -(current_retrievability.powf(-1.0 / decay) - 1.)
                                / (desired_retrievability.powf(-1.0 / decay) - 1.),
                        ));
                    }
                }
            }
            let days_elapsed = secs_elapsed / 86_400;
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

fn trace(event: TraceEvent) {
    if let TraceEvent::Stmt(_, sql) = event {
        println!("sql: {}", sql.trim().replace('\n', " "));
    }
}

impl SqliteStorage {
    pub(crate) fn open_or_create(
        path: &Path,
        tr: &I18n,
        server: bool,
        check_integrity: bool,
    ) -> Result<Self> {
        let db = open_or_create_collection_db(path, !check_integrity)?;
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
            let s =
                db.pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0))?;
            require!(s == "ok", "corrupt: {s}");
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

        if storage.migrate_review_retrievability_cache_to_sidecar()? > 0 {
            storage.mark_review_retrievability_cache_cleanup_full_sync()?;
            storage.set_schema_modified_time(TimestampMillis::now())?;
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
                println!("error: {e:?}");
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

#[derive(Debug, Clone, Copy, PartialEq)]
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
    use anki_io::new_tempfile;

    use super::*;
    use crate::collection::CollectionBuilder;
    use crate::error::DbError;
    use crate::scheduler::answering::test::v3_test_collection;
    use crate::storage::card::ReviewOrderSubclause;
    use crate::tests::NoteAdder;

    fn test_tr() -> I18n {
        I18n::template_only()
    }

    /// Builds a fresh on-disk collection, stamps the given schema version into
    /// the `col` table, closes it without downgrading, then reopens it through
    /// the migration gate and returns the resulting error kind.
    fn open_error_kind_for_schema_version(ver: u8) -> DbErrorKind {
        let tempfile = new_tempfile().unwrap();
        {
            let col = CollectionBuilder::default()
                .set_collection_path(tempfile.path())
                .build()
                .unwrap();
            col.storage
                .db
                .execute("update col set ver = ?", params![ver])
                .unwrap();
            col.close(None).unwrap();
        }
        let err =
            SqliteStorage::open_or_create(tempfile.path(), &test_tr(), false, false).unwrap_err();
        match err {
            AnkiError::DbError {
                source: DbError { kind, .. },
            } => kind,
            other => panic!("expected DbError, got {other:?}"),
        }
    }

    /// Reads the persisted schema version with a raw connection, so no upgrade
    /// is triggered.
    fn stored_schema_version(path: &Path) -> u8 {
        Connection::open(path)
            .unwrap()
            .query_row("select ver from col", [], |r| r.get(0))
            .unwrap()
    }

    #[test]
    fn open_rejects_schema_older_than_minimum() {
        assert_eq!(
            open_error_kind_for_schema_version(SCHEMA_MIN_VERSION - 1),
            DbErrorKind::FileTooOld
        );
    }

    #[test]
    fn open_rejects_schema_newer_than_maximum() {
        assert_eq!(
            open_error_kind_for_schema_version(SCHEMA_MAX_VERSION + 1),
            DbErrorKind::FileTooNew
        );
    }

    #[test]
    fn open_rejects_intermediate_schema_versions_12_and_13() {
        // versions 12 and 13 require a clean shutdown back to 11 first
        assert_eq!(
            open_error_kind_for_schema_version(12),
            DbErrorKind::FileTooNew
        );
        assert_eq!(
            open_error_kind_for_schema_version(13),
            DbErrorKind::FileTooNew
        );
    }

    #[test]
    fn rejected_schema_leaves_version_and_collection_data_unchanged() {
        for ver in [SCHEMA_MIN_VERSION - 1, 12, 13, SCHEMA_MAX_VERSION + 1] {
            let tempfile = new_tempfile().unwrap();
            let mut col = CollectionBuilder::default()
                .set_collection_path(tempfile.path())
                .build()
                .unwrap();
            let note = NoteAdder::basic(&mut col)
                .fields(&["Preserved question", "Preserved answer"])
                .add(&mut col);
            let original_note = col.storage.get_note(note.id).unwrap().unwrap();
            let cards = col.storage.get_all_cards();
            col.storage
                .db
                .execute("update col set ver = ?", params![ver])
                .unwrap();
            let collection_change = TimestampMillis(1_600_000_000_000);
            col.storage.set_modified_time(collection_change).unwrap();
            col.close(None).unwrap();

            SqliteStorage::open_or_create(tempfile.path(), &test_tr(), false, false).unwrap_err();

            assert_eq!(stored_schema_version(tempfile.path()), ver, "schema {ver}");
            // A rejected open must be side-effect free, so the modification time
            // is left untouched (read before restoring the version below).
            let mod_after: i64 = Connection::open(tempfile.path())
                .unwrap()
                .query_row("select mod from col", [], |r| r.get(0))
                .unwrap();
            assert_eq!(mod_after, collection_change.0, "schema {ver}");
            // Only the version marker was changed in setup; restore it to verify
            // that rejection left the collection readable and its data intact.
            Connection::open(tempfile.path())
                .unwrap()
                .execute("update col set ver = ?", params![SCHEMA_MAX_VERSION])
                .unwrap();
            let col = CollectionBuilder::default()
                .set_collection_path(tempfile.path())
                .build()
                .unwrap();
            assert_eq!(
                col.storage.get_note(note.id).unwrap(),
                Some(original_note),
                "schema {ver}"
            );
            assert_eq!(col.storage.get_all_cards(), cards, "schema {ver}");
        }
    }

    #[test]
    fn fresh_collection_is_created_at_latest_schema_version() {
        let tempfile = new_tempfile().unwrap();
        let col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()
            .unwrap();

        let ver: u8 = col.storage.db_scalar("select ver from col").unwrap();

        assert_eq!(ver, SCHEMA_MAX_VERSION);
    }

    #[test]
    fn close_downgrades_stored_schema_version_to_v11() {
        let tempfile = new_tempfile().unwrap();
        let col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()
            .unwrap();

        col.close(Some(SchemaVersion::V11)).unwrap();

        assert_eq!(stored_schema_version(tempfile.path()), SCHEMA_MIN_VERSION);
    }

    #[test]
    fn close_at_latest_version_leaves_schema_unchanged() {
        let tempfile = new_tempfile().unwrap();
        let col = CollectionBuilder::default()
            .set_collection_path(tempfile.path())
            .build()
            .unwrap();

        col.close(Some(SchemaVersion::V18)).unwrap();

        assert_eq!(stored_schema_version(tempfile.path()), SCHEMA_MAX_VERSION);
    }

    #[test]
    fn collection_open_recreates_corrupt_retrievability_cache() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let collection_path = dir.path().join("collection.anki2");
        let cache_path = retrievability_cache_path(&collection_path);
        std::fs::create_dir_all(cache_path.parent().unwrap())?;
        std::fs::write(&cache_path, b"not a database")?;

        let col = crate::collection::CollectionBuilder::new(&collection_path).build()?;
        let count: i64 = col.storage.db.query_row(
            "SELECT count(*) FROM retrievability_cache.search_stats_rwkv_review_retrievability",
            [],
            |row| row.get(0),
        )?;
        assert_eq!(count, 0);
        Ok(())
    }

    #[test]
    fn collection_open_preserves_corrupt_legacy_retrievability_cache() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let collection_path = dir.path().join("collection.anki2");
        let old_path = collection_path.with_extension("retrievability-cache.sqlite");
        std::fs::write(&old_path, b"not a database")?;

        let col = crate::collection::CollectionBuilder::new(&collection_path).build()?;
        let count: i64 = col.storage.db.query_row(
            "SELECT count(*) FROM retrievability_cache.search_stats_rwkv_review_retrievability",
            [],
            |row| row.get(0),
        )?;
        assert_eq!(count, 0);
        assert_eq!(std::fs::read(old_path)?, b"not a database");
        Ok(())
    }

    #[test]
    fn collection_open_preserves_busy_retrievability_caches() -> Result<()> {
        for legacy in [false, true] {
            let dir = tempfile::tempdir()?;
            let collection_path = dir.path().join("collection.anki2");
            let cache_path = if legacy {
                collection_path.with_extension("retrievability-cache.sqlite")
            } else {
                retrievability_cache_path(&collection_path)
            };
            std::fs::create_dir_all(cache_path.parent().unwrap())?;
            let cache = Connection::open(&cache_path)?;
            cache.execute_batch(
                "CREATE TABLE sentinel (value); INSERT INTO sentinel VALUES (42); BEGIN EXCLUSIVE",
            )?;

            let err = crate::collection::CollectionBuilder::new(&collection_path)
                .build()
                .err()
                .unwrap();
            assert!(
                matches!(err, AnkiError::DbError { source } if source.kind == DbErrorKind::Locked)
            );
            assert_eq!(
                cache.query_row("SELECT value FROM sentinel", [], |row| row.get::<_, i64>(0))?,
                42
            );
            assert!(cache_path.is_file());
        }
        Ok(())
    }

    /// A collection path just under Windows' MAX_PATH (260 characters), so
    /// its cache paths, which are longer, go past it.
    fn long_collection_path(dir: &Path) -> Result<PathBuf> {
        const COLLECTION_PATH_LEN: usize = 240;
        let file_name = "collection.anki2";
        let pad = COLLECTION_PATH_LEN - dir.join(file_name).as_os_str().len() - 1;
        let collection_dir = dir.join("d".repeat(pad));
        std::fs::create_dir_all(&collection_dir)?;
        Ok(collection_dir.join(file_name))
    }

    #[test]
    fn collection_open_attaches_cache_past_max_path() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let collection_path = long_collection_path(dir.path())?;
        let cache_path = retrievability_cache_path(&collection_path);
        assert!(cache_path.as_os_str().len() >= 260);

        let col = CollectionBuilder::new(&collection_path).build()?;
        let count: i64 = col.storage.db.query_row(
            "SELECT count(*) FROM retrievability_cache.search_stats_rwkv_review_retrievability",
            [],
            |row| row.get(0),
        )?;
        assert_eq!(count, 0);
        assert!(cache_path.is_file());
        Ok(())
    }

    #[test]
    fn collection_open_relocates_cache_past_max_path() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let collection_path = long_collection_path(dir.path())?;
        let old_path = collection_path.with_extension("retrievability-cache.sqlite");
        assert!(old_path.as_os_str().len() >= 260);

        // Create the legacy cache at a short path; std moves it past MAX_PATH.
        let short_path = dir.path().join("legacy.sqlite");
        Connection::open(&short_path)?.execute_batch("CREATE TABLE sentinel (value)")?;
        std::fs::rename(&short_path, &old_path)?;

        let col = CollectionBuilder::new(&collection_path).build()?;
        let sentinels: i64 = col.storage.db.query_row(
            "SELECT count(*) FROM retrievability_cache.sqlite_master WHERE name = 'sentinel'",
            [],
            |row| row.get(0),
        )?;
        assert_eq!(sentinels, 1);
        assert!(!old_path.exists());
        Ok(())
    }

    #[test]
    fn regexp_fields_matches_only_selected_fields() -> rusqlite::Result<()> {
        let db = Connection::open_in_memory()?;
        add_regexp_fields_function(&db)?;
        let fields = "猫\x1f\x1fかな\x1fカナ\x1fignored";
        for (pattern, indices, expected) in [
            ("^かな$", "", true),
            ("^かな$", ", 0", false),
            ("^かな$", ", 2", true),
            ("^$", ", 1", true),
            ("^かな$", ", 3, 2, 0, 2", true),
            ("^ignored$", ", 0, 2", false),
            ("^ignored$", ", 4", true),
            ("^$", ", 5", false),
        ] {
            let matched: bool = db.query_row(
                &format!("select regexp_fields(?1, ?2{indices})"),
                [pattern, fields],
                |row| row.get(0),
            )?;
            assert_eq!(matched, expected, "pattern={pattern}, indices={indices}");
        }
        Ok(())
    }

    #[test]
    fn missing_memory_state_falls_back_to_sm2() -> Result<()> {
        let (mut col, _cids) = v3_test_collection(1)?;
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        col.answer_easy();

        let timing = col.timing_today()?;
        let sql_func = ReviewOrderSubclause::RelativeOverdueness { fsrs: true, timing }
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
