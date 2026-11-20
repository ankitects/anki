// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;
use std::convert::TryFrom;

use rusqlite::params;
use rusqlite::types::FromSql;
use rusqlite::types::FromSqlError;
use rusqlite::types::ValueRef;
use rusqlite::OptionalExtension;
use rusqlite::Row;

use super::ids_to_string;
use super::sqlite::RETRIEVABILITY_CACHE_DB_SCHEMA;
use super::SqliteStorage;
use crate::config::ConfigEntry;
use crate::error::Result;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;

pub(crate) const FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE: &str =
    "search_stats_fsrs_review_retrievability";
pub(crate) const RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE: &str =
    "search_stats_rwkv_review_retrievability";
const REVIEW_RETRIEVABILITY_CACHE_CLEANUP_FULL_SYNC_MARKER: &str =
    "reviewRetrievabilityCacheCleanupFullSync";
const REVIEW_RETRIEVABILITY_CACHE_WRITE_SAVEPOINT: &str = "review_retrievability_cache_write";
/// Effective desktop-compatible reset boundary for the card aliased as `c`.
pub(crate) const RWKV_HISTORY_RESET_AFTER_SQL: &str = "coalesce(
    (select case when json_valid(cast(val as text)) then
       case when json_type(cast(val as text), '$.after') = 'integer'
       then max(json_extract(cast(val as text), '$.after'), 0) end end
     from config where key = 'rwkvHistoryReset_' || c.id),
    case when json_valid(c.data) then
      case when json_type(c.data, '$.rwkv_reset_after') = 'integer'
      then max(json_extract(c.data, '$.rwkv_reset_after'), 0) else 0 end else 0 end)";
const FSRS_REVIEW_RETRIEVABILITY_SAMPLE_ROLES: &str =
    "'final_fit', 'validation_fold', 'post_optimization'";
const RWKV_REVIEW_RETRIEVABILITY_SAMPLE_ROLES: &str =
    "'final_fit', 'test_fold', 'validation_fold', 'post_optimization'";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum FsrsReviewRetrievabilitySampleRole {
    FinalFit,
    ValidationFold,
    PostOptimization,
}

impl FsrsReviewRetrievabilitySampleRole {
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Self::FinalFit => "final_fit",
            Self::ValidationFold => "validation_fold",
            Self::PostOptimization => "post_optimization",
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct FsrsReviewRetrievabilityCacheRow {
    pub revlog_id: RevlogId,
    pub prediction: f32,
    pub sample_role: FsrsReviewRetrievabilitySampleRole,
    pub fold_index: i32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum RwkvReviewRetrievabilitySampleRole {
    FinalFit,
    TestFold,
    PostOptimization,
}

impl RwkvReviewRetrievabilitySampleRole {
    pub(crate) fn as_str(self) -> &'static str {
        match self {
            Self::FinalFit => "final_fit",
            Self::TestFold => "test_fold",
            Self::PostOptimization => "post_optimization",
        }
    }

    pub(crate) fn from_str(value: &str) -> Option<Self> {
        match value {
            "" | "final_fit" => Some(Self::FinalFit),
            "test_fold" | "validation_fold" => Some(Self::TestFold),
            "post_optimization" => Some(Self::PostOptimization),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct RwkvReviewRetrievabilityCacheRow {
    pub revlog_id: RevlogId,
    pub prediction: f32,
    pub sample_role: RwkvReviewRetrievabilitySampleRole,
    pub fold_index: i32,
}

pub(crate) struct StudiedToday {
    pub cards: u32,
    pub seconds: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct RwkvHistoricalReviewRow {
    pub(crate) review_id: i64,
    pub(crate) card_id: i64,
    pub(crate) note_id: i64,
    pub(crate) deck_id: i64,
    pub(crate) ease: i64,
    pub(crate) duration_millis: i64,
    pub(crate) review_kind: i64,
    pub(crate) interval_days: i64,
    pub(crate) ease_factor: i64,
    pub(crate) is_learning_start: bool,
}

impl FromSql for RevlogReviewKind {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_revlog_entry(row: &Row) -> Result<RevlogEntry> {
    Ok(RevlogEntry {
        id: row.get(0)?,
        cid: row.get(1)?,
        usn: row.get(2)?,
        button_chosen: row.get(3)?,
        interval: row.get(4)?,
        last_interval: row.get(5)?,
        ease_factor: row.get(6)?,
        taken_millis: row.get(7).unwrap_or_default(),
        review_kind: row.get(8).unwrap_or_default(),
    })
}

impl SqliteStorage {
    fn qualified_retrievability_cache_table(table: &str) -> String {
        format!("{RETRIEVABILITY_CACHE_DB_SCHEMA}.{table}")
    }

    fn table_columns(&self, schema: &str, table: &str) -> Result<Vec<String>> {
        self.db
            .prepare(&format!("PRAGMA {schema}.table_info({table})"))?
            .query_map([], |row| row.get::<_, String>(1))?
            .collect::<std::result::Result<Vec<_>, _>>()
            .map_err(Into::into)
    }

    fn main_table_exists(&self, table: &str) -> Result<bool> {
        self.db
            .prepare("SELECT null FROM main.sqlite_master WHERE type = 'table' AND name = ?")?
            .exists([table])
            .map_err(Into::into)
    }

    fn migrate_legacy_retrievability_cache_table(
        &self,
        table: &str,
        valid_sample_roles: &str,
    ) -> Result<bool> {
        if !self.main_table_exists(table)? {
            return Ok(false);
        }

        let columns = self.table_columns("main", table)?;
        let has_required_columns = ["revlog_id", "prediction", "source", "updated_at"]
            .iter()
            .all(|required| columns.iter().any(|column| column == required));
        if has_required_columns {
            let has_sample_role = columns.iter().any(|column| column == "sample_role");
            let has_fold_index = columns.iter().any(|column| column == "fold_index");
            let sample_role = if has_sample_role {
                "sample_role"
            } else {
                "'final_fit'"
            };
            let fold_index = if has_fold_index { "fold_index" } else { "-1" };
            let sample_role_filter = if has_sample_role {
                format!("AND sample_role IN ({valid_sample_roles})")
            } else {
                String::new()
            };
            let target = Self::qualified_retrievability_cache_table(table);
            self.db.execute_batch(&format!(
                "
                INSERT OR REPLACE INTO {target}
                    (revlog_id, prediction, source, updated_at, sample_role, fold_index)
                SELECT revlog_id, prediction, source, updated_at, {sample_role}, {fold_index}
                FROM main.{table}
                WHERE revlog_id > 0
                  AND prediction BETWEEN 0 AND 1
                  AND source IS NOT NULL
                  AND updated_at IS NOT NULL
                  {sample_role_filter};
                "
            ))?;
        }

        self.db
            .execute_batch(&format!("DROP TABLE IF EXISTS main.{table};"))?;
        Ok(true)
    }

    pub(crate) fn migrate_review_retrievability_cache_to_sidecar(&self) -> Result<usize> {
        self.ensure_fsrs_review_retrievability_cache_schema()?;
        let mut dropped = 0;
        if self.migrate_legacy_retrievability_cache_table(
            FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE,
            FSRS_REVIEW_RETRIEVABILITY_SAMPLE_ROLES,
        )? {
            dropped += 1;
        }
        self.ensure_rwkv_review_retrievability_cache_schema()?;
        if self.migrate_legacy_retrievability_cache_table(
            RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE,
            RWKV_REVIEW_RETRIEVABILITY_SAMPLE_ROLES,
        )? {
            dropped += 1;
        }
        Ok(dropped)
    }

    pub(crate) fn clear_review_retrievability_cache(&self) -> Result<()> {
        self.ensure_fsrs_review_retrievability_cache_schema()?;
        self.ensure_rwkv_review_retrievability_cache_schema()?;
        self.with_retrievability_cache_write_batch(|| {
            for table in [
                FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE,
                RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE,
            ] {
                self.db.execute(
                    &format!(
                        "DELETE FROM {}",
                        Self::qualified_retrievability_cache_table(table)
                    ),
                    [],
                )?;
            }
            Ok(0)
        })?;
        Ok(())
    }

    pub(crate) fn review_retrievability_cache_cleanup_full_sync_marked(&self) -> Result<bool> {
        Ok(self
            .get_config_value::<bool>(REVIEW_RETRIEVABILITY_CACHE_CLEANUP_FULL_SYNC_MARKER)?
            .unwrap_or(false))
    }

    pub(crate) fn mark_review_retrievability_cache_cleanup_full_sync(&self) -> Result<()> {
        self.set_config_entry(&ConfigEntry::boxed(
            REVIEW_RETRIEVABILITY_CACHE_CLEANUP_FULL_SYNC_MARKER,
            serde_json::to_vec(&true)?,
            Usn(0),
            TimestampSecs::now(),
        ))
    }

    fn with_retrievability_cache_write_batch(
        &self,
        op: impl FnOnce() -> Result<usize>,
    ) -> Result<usize> {
        let started_savepoint = self.db.is_autocommit();
        if started_savepoint {
            self.db.execute_batch(&format!(
                "SAVEPOINT {REVIEW_RETRIEVABILITY_CACHE_WRITE_SAVEPOINT};"
            ))?;
        }

        let result = op();
        if !started_savepoint {
            return result;
        }

        match result {
            Ok(stored) => {
                self.db.execute_batch(&format!(
                    "RELEASE {REVIEW_RETRIEVABILITY_CACHE_WRITE_SAVEPOINT};"
                ))?;
                Ok(stored)
            }
            Err(err) => {
                if let Err(rollback_err) = self.db.execute_batch(&format!(
                    "ROLLBACK TO {REVIEW_RETRIEVABILITY_CACHE_WRITE_SAVEPOINT};
                     RELEASE {REVIEW_RETRIEVABILITY_CACHE_WRITE_SAVEPOINT};"
                )) {
                    tracing::warn!(
                        ?rollback_err,
                        "failed to roll back retrievability cache write batch"
                    );
                }
                Err(err)
            }
        }
    }

    fn ensure_fsrs_review_retrievability_cache_schema(&self) -> Result<()> {
        let table_info = self.table_columns(
            RETRIEVABILITY_CACHE_DB_SCHEMA,
            FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE,
        )?;
        let has_sample_role = table_info.iter().any(|col| col == "sample_role");
        let has_fold_index = table_info.iter().any(|col| col == "fold_index");
        if !table_info.is_empty() && (!has_sample_role || !has_fold_index) {
            let table =
                Self::qualified_retrievability_cache_table(FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE);
            self.db
                .execute_batch(&format!("DROP TABLE IF EXISTS {table};"))?;
        }

        let table =
            Self::qualified_retrievability_cache_table(FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE);
        self.db.execute_batch(&format!(
            "
            CREATE TABLE IF NOT EXISTS {table} (
                revlog_id INTEGER NOT NULL,
                prediction REAL NOT NULL CHECK(prediction >= 0 AND prediction <= 1),
                source TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sample_role TEXT NOT NULL DEFAULT 'final_fit'
                    CHECK(sample_role IN ('final_fit', 'validation_fold', 'post_optimization')),
                fold_index INTEGER NOT NULL DEFAULT -1,
                PRIMARY KEY (revlog_id, sample_role, fold_index, source)
            );
            CREATE INDEX IF NOT EXISTS {RETRIEVABILITY_CACHE_DB_SCHEMA}.ix_fsrs_review_retrievability_role_revlog
                ON {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} (sample_role, revlog_id);
            "
        ))?;
        Ok(())
    }

    pub(crate) fn set_fsrs_review_retrievability_predictions(
        &self,
        rows: &[FsrsReviewRetrievabilityCacheRow],
        source: &str,
    ) -> Result<usize> {
        if rows.is_empty() {
            return Ok(0);
        }

        self.ensure_fsrs_review_retrievability_cache_schema()?;

        self.with_retrievability_cache_write_batch(|| {
            let updated_at = TimestampMillis::now().0;
            let mut stored = 0;
            let table =
                Self::qualified_retrievability_cache_table(FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE);
            let mut stmt = self.db.prepare_cached(&format!(
                "
                INSERT INTO {table}
                    (revlog_id, prediction, source, updated_at, sample_role, fold_index)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(revlog_id, sample_role, fold_index, source) DO UPDATE SET
                    prediction = excluded.prediction,
                    updated_at = excluded.updated_at
                WHERE {table}.prediction IS NOT excluded.prediction
                "
            ))?;

            for row in rows {
                if row.revlog_id.0 > 0
                    && row.prediction.is_finite()
                    && (0.0..=1.0).contains(&row.prediction)
                {
                    stmt.execute(params![
                        row.revlog_id,
                        row.prediction,
                        source,
                        updated_at,
                        row.sample_role.as_str(),
                        row.fold_index
                    ])?;
                    stored += 1;
                }
            }

            Ok(stored)
        })
    }

    fn ensure_rwkv_review_retrievability_cache_schema(&self) -> Result<()> {
        let table_info = self.table_columns(
            RETRIEVABILITY_CACHE_DB_SCHEMA,
            RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE,
        )?;
        let required_columns = ["revlog_id", "prediction", "source", "updated_at"];
        let has_sample_role = table_info.iter().any(|col| col == "sample_role");
        let has_fold_index = table_info.iter().any(|col| col == "fold_index");
        if !table_info.is_empty()
            && (!required_columns
                .iter()
                .all(|required| table_info.iter().any(|column| column == required))
                || !has_sample_role
                || !has_fold_index)
        {
            let table =
                Self::qualified_retrievability_cache_table(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE);
            self.db
                .execute_batch(&format!("DROP TABLE IF EXISTS {table};"))?;
        }

        let table =
            Self::qualified_retrievability_cache_table(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE);
        self.db.execute_batch(&format!(
            "
            CREATE TABLE IF NOT EXISTS {table} (
                revlog_id INTEGER NOT NULL,
                prediction REAL NOT NULL CHECK(prediction >= 0 AND prediction <= 1),
                source TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sample_role TEXT NOT NULL DEFAULT 'final_fit'
                    CHECK(sample_role IN ('final_fit', 'test_fold', 'validation_fold', 'post_optimization')),
                fold_index INTEGER NOT NULL DEFAULT -1,
                PRIMARY KEY (revlog_id, sample_role, fold_index, source)
            );
            CREATE INDEX IF NOT EXISTS {RETRIEVABILITY_CACHE_DB_SCHEMA}.ix_rwkv_review_retrievability_role_revlog
                ON {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} (sample_role, revlog_id);
            "
        ))?;
        Ok(())
    }

    pub(crate) fn set_rwkv_review_retrievability_predictions(
        &self,
        rows: &[RwkvReviewRetrievabilityCacheRow],
        source: &str,
    ) -> Result<usize> {
        if rows.is_empty() {
            return Ok(0);
        }

        self.ensure_rwkv_review_retrievability_cache_schema()?;

        self.with_retrievability_cache_write_batch(|| {
            let updated_at = TimestampMillis::now().0;
            let mut stored = 0;
            let table =
                Self::qualified_retrievability_cache_table(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE);
            let mut stmt = self.db.prepare_cached(&format!(
                "
                INSERT INTO {table}
                    (revlog_id, prediction, source, updated_at, sample_role, fold_index)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(revlog_id, sample_role, fold_index, source) DO UPDATE SET
                    prediction = excluded.prediction,
                    updated_at = excluded.updated_at
                WHERE {table}.prediction IS NOT excluded.prediction
                "
            ))?;
            for row in rows {
                if row.revlog_id.0 > 0
                    && row.prediction.is_finite()
                    && (0.0..=1.0).contains(&row.prediction)
                {
                    stmt.execute(params![
                        row.revlog_id,
                        row.prediction,
                        source,
                        updated_at,
                        row.sample_role.as_str(),
                        row.fold_index,
                    ])?;
                    stored += 1;
                }
            }
            Ok(stored)
        })
    }

    pub(crate) fn set_rwkv_review_retrievability_prediction(
        &self,
        revlog_id: RevlogId,
        prediction: f32,
        source: &str,
    ) -> Result<()> {
        self.set_rwkv_review_retrievability_predictions(
            &[RwkvReviewRetrievabilityCacheRow {
                revlog_id,
                prediction,
                sample_role: RwkvReviewRetrievabilitySampleRole::PostOptimization,
                fold_index: -1,
            }],
            source,
        )
        .map(|_| ())
    }

    /// Deletes rows of `source` whose (revlog id, role, fold) is not in `keep`,
    /// and every row of `superseded_sources`. A recompute only rewrites rows
    /// whose prediction changed, so rows from earlier runs survive under roles
    /// the latest run no longer assigns, and readers taking the newest row
    /// could pick one of them.
    pub(crate) fn prune_rwkv_review_retrievability_rows(
        &self,
        source: &str,
        keep: &[(RevlogId, RwkvReviewRetrievabilitySampleRole, i32)],
        superseded_sources: &[String],
    ) -> Result<usize> {
        // An empty run never supersedes existing calibration data.
        if keep.is_empty() {
            return Ok(0);
        }

        self.ensure_rwkv_review_retrievability_cache_schema()?;

        self.with_retrievability_cache_write_batch(|| {
            self.db.execute_batch(
                "
                CREATE TEMP TABLE IF NOT EXISTS rwkv_calibration_keep (
                    revlog_id INTEGER NOT NULL,
                    sample_role TEXT NOT NULL,
                    fold_index INTEGER NOT NULL,
                    PRIMARY KEY (revlog_id, sample_role, fold_index)
                ) WITHOUT ROWID;
                DELETE FROM temp.rwkv_calibration_keep;
                ",
            )?;
            let mut insert = self.db.prepare_cached(
                "INSERT OR IGNORE INTO temp.rwkv_calibration_keep VALUES (?, ?, ?)",
            )?;
            for (revlog_id, sample_role, fold_index) in keep {
                insert.execute(params![revlog_id, sample_role.as_str(), fold_index])?;
            }

            let table =
                Self::qualified_retrievability_cache_table(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE);
            let mut deleted = self.db.execute(
                &format!(
                    "
                    DELETE FROM {table}
                    WHERE source = ?
                      AND NOT EXISTS (
                        SELECT 1 FROM temp.rwkv_calibration_keep keep
                        WHERE keep.revlog_id = {table}.revlog_id
                          AND keep.sample_role = {table}.sample_role
                          AND keep.fold_index = {table}.fold_index
                      )
                    "
                ),
                [source],
            )?;
            if !superseded_sources.is_empty() {
                let placeholders = vec!["?"; superseded_sources.len()].join(", ");
                deleted += self.db.execute(
                    &format!("DELETE FROM {table} WHERE source IN ({placeholders})"),
                    rusqlite::params_from_iter(superseded_sources),
                )?;
            }
            self.db
                .execute_batch("DROP TABLE temp.rwkv_calibration_keep;")?;
            Ok(deleted)
        })
    }

    pub(crate) fn fix_revlog_properties(&self) -> Result<usize> {
        self.db
            .prepare(include_str!("fix_props.sql"))?
            .execute([])
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_revlog_usns(&self) -> Result<()> {
        self.db
            .prepare("update revlog set usn = 0 where usn = -1")?
            .execute([])?;
        Ok(())
    }

    /// Adds the entry, if its id is unique. If it is not, and `uniquify` is
    /// true, adds it with a new id. Returns the added id.
    /// (I.e., the option is safe to unwrap, if `uniquify` is true.)
    pub(crate) fn add_revlog_entry(
        &self,
        entry: &RevlogEntry,
        uniquify: bool,
    ) -> Result<Option<RevlogId>> {
        let added = self
            .db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                uniquify,
                entry.id,
                entry.cid,
                entry.usn,
                entry.button_chosen,
                entry.interval,
                entry.last_interval,
                entry.ease_factor,
                entry.taken_millis,
                entry.review_kind as u8
            ])?;
        Ok((added > 0).then(|| RevlogId(self.db.last_insert_rowid())))
    }

    pub(crate) fn get_revlog_entry(&self, id: RevlogId) -> Result<Option<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id=?"))?
            .query_and_then([id], row_to_revlog_entry)?
            .next()
            .transpose()
    }

    /// Determine the the last review time based on the revlog.
    pub(crate) fn time_of_last_review(&self, card_id: CardId) -> Result<Option<TimestampSecs>> {
        self.db
            .prepare_cached(include_str!("time_of_last_review.sql"))?
            .query_row([card_id], |row| row.get(0))
            .optional()
            .map_err(Into::into)
    }

    /// The newest scheduling answer not followed by a Forget reset.
    pub(crate) fn last_unreset_review(&self, card_id: CardId) -> Result<Option<RevlogId>> {
        self.db
            .prepare_cached(
                "select id from revlog
                 where cid = ?1 and ease between 1 and 4
                   and type in (0, 1, 2, 3, 4, 5)
                   and not (type = 3 and factor = 0)
                   and id > coalesce((
                     select max(id) from revlog
                     where cid = ?1 and type = 4 and ease = 0 and factor = 0
                   ), 0)
                 order by id desc limit 1",
            )?
            .query_row([card_id], |row| row.get(0))
            .optional()
            .map_err(Into::into)
    }

    #[cfg(test)]
    pub(crate) fn times_of_last_review(
        &self,
        card_ids: &[CardId],
    ) -> Result<HashMap<CardId, TimestampSecs>> {
        if card_ids.is_empty() {
            return Ok(HashMap::new());
        }

        let mut ids = String::new();
        ids_to_string(&mut ids, card_ids);
        let sql = format!(
            "select id, (select revlog.id / 1000 from revlog \
             where cid = cards.id and ease between 1 and 4 \
             and (type != 3 or factor != 0) \
             order by revlog.id desc limit 1) from cards where id in {ids}"
        );
        let mut review_times = HashMap::new();
        let mut stmt = self.db.prepare(&sql)?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let cid: CardId = row.get(0)?;
            let last_review_time: Option<TimestampSecs> = row.get(1)?;
            if let Some(time) = last_review_time {
                review_times.insert(cid, time);
            }
        }

        Ok(review_times)
    }

    pub(crate) fn rwkv_times_of_last_review(
        &self,
        card_ids: &[CardId],
    ) -> Result<HashMap<CardId, TimestampSecs>> {
        if card_ids.is_empty() {
            return Ok(HashMap::new());
        }
        let mut ids = String::new();
        ids_to_string(&mut ids, card_ids);
        let sql = format!(
            "select c.id, (select r.id / 1000 from revlog r
             where r.cid = c.id and r.ease between 1 and 4
               and r.type in (0, 1, 2, 3, 4, 5)
               and not (r.type = 3 and r.factor = 0)
               and r.id > coalesce((select max(id) from revlog
                 where cid = c.id and type = 4 and ease = 0 and factor = 0), 0)
               and r.id > ({RWKV_HISTORY_RESET_AFTER_SQL})
             order by r.id desc limit 1) from cards c where c.id in {ids}"
        );
        let mut stmt = self.db.prepare(&sql)?;
        let mut rows = stmt.query([])?;
        let mut review_times = HashMap::new();
        while let Some(row) = rows.next()? {
            let cid: CardId = row.get(0)?;
            if let Some(time) = row.get::<_, Option<TimestampSecs>>(1)? {
                review_times.insert(cid, time);
            }
        }
        Ok(review_times)
    }

    /// Counts since the most recent answer for cards still inside the repeat
    /// spacing window. Other cards have already met the requested minimum.
    pub(crate) fn recent_rwkv_intervening_reviews(
        &self,
        deck_ids: &[DeckId],
        minimum_reviews: u32,
    ) -> Result<HashMap<CardId, u32>> {
        let mut counts = HashMap::new();
        if minimum_reviews == 0 {
            return Ok(counts);
        }
        let mut ids = String::new();
        ids_to_string(&mut ids, deck_ids);
        let sql = format!(
            "select r.cid from revlog r join cards c on c.id = r.cid \
             where r.ease between 1 and 4 and r.type in (0, 1, 2, 3, 4, 5) \
             and not (r.type = 3 and r.factor = 0) \
             and (case when c.odid != 0 then c.odid else c.did end) in {ids} \
             order by r.id desc limit ?"
        );
        let mut stmt = self.db.prepare(&sql)?;
        let mut rows = stmt.query([minimum_reviews])?;
        let mut count = 0;
        while let Some(row) = rows.next()? {
            counts.entry(row.get(0)?).or_insert(count);
            count += 1;
        }
        Ok(counts)
    }

    /// Cards with a genuine answer in the half-open timestamp range.
    pub(crate) fn card_ids_reviewed_between(
        &self,
        start: TimestampSecs,
        end: TimestampSecs,
    ) -> Result<HashSet<CardId>> {
        self.db
            .prepare_cached(
                "select distinct cid from revlog \
                 where id >= ? and id < ? \
                 and ease between 1 and 4 \
                 and (type != 3 or factor != 0)",
            )?
            .query_map([start.0 * 1000, end.0 * 1000], |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()
            .map_err(Into::into)
    }

    /// Only intended to be used by the undo code, as Anki can not sync revlog
    /// deletions.
    pub(crate) fn remove_revlog_entry(&self, id: RevlogId) -> Result<()> {
        self.db
            .prepare_cached("delete from revlog where id = ?")?
            .execute([id])?;
        Ok(())
    }

    pub(crate) fn get_revlog_entries_for_card(&self, cid: CardId) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where cid=?"))?
            .query_and_then([cid], row_to_revlog_entry)?
            .collect()
    }

    #[cfg(test)]
    pub(crate) fn rwkv_historical_review_rows(
        &self,
        ignored_review_ids: &[RevlogId],
    ) -> Result<(Vec<RwkvHistoricalReviewRow>, Vec<i64>)> {
        self.rwkv_historical_review_rows_with_cutoffs(ignored_review_ids, &HashMap::new(), None)
            .map(|(rows, active, _)| (rows, active))
    }

    /// Also returns how many revlog rows were scanned and the newest one's id:
    /// the whole revlog without `card_id` and ignored ids, so the replay stamp
    /// needn't count it again.
    pub(crate) fn rwkv_historical_review_rows_with_cutoffs(
        &self,
        ignored_review_ids: &[RevlogId],
        preserved_learning_start_cutoffs: &HashMap<i64, i64>,
        // Only this card's reviews (what is kept of a card depends only on its own).
        card_id: Option<CardId>,
    ) -> Result<(Vec<RwkvHistoricalReviewRow>, Vec<i64>, (u64, i64))> {
        let (ignored_clause, active_ignored_review_ids) = if ignored_review_ids.is_empty() {
            (String::new(), Vec::new())
        } else {
            let mut ids = String::new();
            ids_to_string(&mut ids, ignored_review_ids);
            let active_sql = format!(
                "select r.id
                 from revlog r
                 join cards c on c.id = r.cid
                 where r.ease between 1 and 4
                   and r.type in (0, 1, 2, 3, 4, 5)
                   and not (r.type = 3 and r.factor = 0)
                   and c.type != 0
                   and r.id > coalesce((select max(id) from revlog
                     where cid = r.cid and type = 4 and ease = 0 and factor = 0), 0)
                   and r.id > ({RWKV_HISTORY_RESET_AFTER_SQL})
                   and r.id in {ids}
                 order by r.id"
            );
            let active = self
                .db
                .prepare(&active_sql)?
                .query_map([], |row| row.get(0))?
                .collect::<std::result::Result<Vec<i64>, _>>()?;
            (format!("and r.id not in {ids}"), active)
        };
        let card_clause = card_id.map_or(String::new(), |id| format!("and r.cid = {}", id.0));
        // The cards and resets are joined here, not in SQL: a cards lookup per
        // review and the resets read through the cid index (random reads into
        // the table) made the query 5.6 s for 3.8M reviews, against 0.9 s for
        // the scan of revlog alone. One card's resets do come through the
        // index: the read after each answer used to compute every card's (3.3 s).
        let (cards_filter, resets_scan, resets_card) = match card_id {
            Some(id) => (
                format!("where id = {}", id.0),
                "",
                format!("and cid = {}", id.0),
            ),
            None => (String::new(), "not indexed", String::new()),
        };
        let cards: HashMap<i64, (i64, i64, i64, i64)> = self
            .db
            .prepare(&format!(
                "select c.id, c.nid, case when c.odid != 0 then c.odid else c.did end,
                        c.type, ({RWKV_HISTORY_RESET_AFTER_SQL}) from cards c {cards_filter}"
            ))?
            .query_map([], |row| {
                Ok((
                    row.get(0)?,
                    (row.get(1)?, row.get(2)?, row.get(3)?, row.get(4)?),
                ))
            })?
            .collect::<std::result::Result<_, _>>()?;
        // Forget keeps the revlog. Honor its reset boundary even before the
        // first new answer, including requests carrying legacy cutoffs.
        let latest_resets: HashMap<i64, i64> = self
            .db
            .prepare(&format!(
                "select cid, max(id) from revlog {resets_scan}
                 where type = 4 and ease = 0 and factor = 0 {resets_card} group by cid"
            ))?
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect::<std::result::Result<_, _>>()?;
        // Rows come back in rowid (review id) order, so no sort or window
        // function is needed; the per-card replay start is derived below.
        // Which answers count is decided here too, so every row is seen.
        let sql = format!(
            "select r.id, r.cid, r.ease, r.time, r.type, cast(r.ivl as integer),
  cast(r.factor as integer)
from revlog r
where 1 {ignored_clause} {card_clause}
order by r.id"
        );
        let mut rows = Vec::new();
        let mut scanned = (0, 0);
        let mut stmt = self.db.prepare(&sql)?;
        let mut query = stmt.query([])?;
        while let Some(row) = query.next()? {
            let review_id: i64 = row.get(0)?;
            scanned = (scanned.0 + 1, review_id);
            let ease: i64 = row.get(2)?;
            let review_kind: i64 = row.get(4)?;
            let ease_factor: i64 = row.get(6)?;
            if !(1..=4).contains(&ease)
                || !(0..=5).contains(&review_kind)
                || (review_kind == 3 && ease_factor == 0)
            {
                continue;
            }
            let card_id: i64 = row.get(1)?;
            let Some(&(note_id, deck_id, card_type, reset_after)) = cards.get(&card_id) else {
                continue;
            };
            if card_type == 0 || review_id <= reset_after {
                continue;
            }
            if latest_resets
                .get(&card_id)
                .is_some_and(|&reset_id| review_id <= reset_id)
            {
                continue;
            }
            rows.push(RwkvHistoricalReviewRow {
                review_id,
                card_id,
                note_id,
                deck_id,
                ease,
                duration_millis: row.get(3)?,
                review_kind,
                interval_days: row.get(5)?,
                ease_factor,
                is_learning_start: false,
            });
        }
        // A cutoff needs a retained prefix to preserve. A legacy cutoff before
        // Forget must not turn the first post-reset Learning answer into a
        // continuation of the discarded sequence.
        let mut first_review_ids = HashMap::new();
        if !preserved_learning_start_cutoffs.is_empty() {
            for row in &rows {
                first_review_ids.entry(row.card_id).or_insert(row.review_id);
            }
        }
        let cutoffs = preserved_learning_start_cutoffs
            .iter()
            .filter(|(card_id, cutoff)| {
                first_review_ids
                    .get(*card_id)
                    .is_some_and(|first| first <= *cutoff)
            })
            .map(|(&card_id, &cutoff)| (card_id, cutoff))
            .collect();
        retain_rwkv_rows_from_latest_learning_start(&mut rows, &cutoffs);

        Ok((rows, active_ignored_review_ids, scanned))
    }

    pub(crate) fn get_revlog_entries_for_searched_cards_after_stamp(
        &self,
        after: TimestampSecs,
    ) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids) and id >= ?"
            ))?
            .query_and_then([after.0 * 1000], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids)"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_searched_cards_in_card_order(
        &self,
    ) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids) order by cid, id"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_revlog_entries_for_export_dataset(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where (ease between 1 and 4) or (ease = 0 and factor = 0)",
                " order by cid, id"
            ))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_all_revlog_entries_in_card_order(&self) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " order by cid, id"))?
            .query_and_then([], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn get_all_revlog_entries(&self, after: TimestampSecs) -> Result<Vec<RevlogEntry>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where id >= ?"))?
            .query_and_then([after.0 * 1000], row_to_revlog_entry)?
            .collect()
    }

    pub(crate) fn studied_today(&self, day_cutoff: TimestampSecs) -> Result<StudiedToday> {
        let start = day_cutoff.adding_secs(-86_400).as_millis();
        self.db
            .prepare_cached(include_str!("studied_today.sql"))?
            .query_map(
                [
                    start.0,
                    RevlogReviewKind::Manual as i64,
                    RevlogReviewKind::Rescheduled as i64,
                ],
                |row| {
                    Ok(StudiedToday {
                        cards: row.get(0)?,
                        seconds: row.get(1)?,
                    })
                },
            )?
            .next()
            .unwrap()
            .map_err(Into::into)
    }

    pub(crate) fn studied_today_by_deck(
        &self,
        day_cutoff: TimestampSecs,
    ) -> Result<Vec<(DeckId, usize)>> {
        let start = day_cutoff.adding_secs(-86_400).as_millis();
        self.db
            .prepare_cached(include_str!("studied_today_by_deck.sql"))?
            .query_and_then([start.0], |row| -> Result<_> {
                Ok((DeckId(row.get(0)?), row.get(1)?))
            })?
            .collect()
    }
    pub(crate) fn upgrade_revlog_to_v2(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("v2_upgrade.sql"))
            .map_err(Into::into)
    }
}

/// Drops each card's reviews before its latest learning start, where a
/// learning start is a learning review not preceded by another learning
/// review. Cards without one keep their full history. `rows` must be in
/// review id order. A preserved cutoff prevents later learning sequences from
/// replacing that card's replay start; those later reviews are still replayed.
fn retain_rwkv_rows_from_latest_learning_start(
    rows: &mut Vec<RwkvHistoricalReviewRow>,
    preserved_learning_start_cutoffs: &HashMap<i64, i64>,
) {
    struct CardReplayStart {
        previous_kind: i64,
        latest_learning_start: Option<i64>,
        first_review_id: i64,
    }
    let mut starts: HashMap<i64, CardReplayStart> = HashMap::new();
    for row in rows.iter() {
        let may_start_learning = row.review_kind == 0
            && !preserved_learning_start_cutoffs
                .get(&row.card_id)
                .is_some_and(|cutoff| row.review_id > *cutoff);
        match starts.entry(row.card_id) {
            std::collections::hash_map::Entry::Vacant(entry) => {
                entry.insert(CardReplayStart {
                    previous_kind: row.review_kind,
                    latest_learning_start: may_start_learning.then_some(row.review_id),
                    first_review_id: row.review_id,
                });
            }
            std::collections::hash_map::Entry::Occupied(mut entry) => {
                let start = entry.get_mut();
                if may_start_learning && start.previous_kind != 0 {
                    start.latest_learning_start = Some(row.review_id);
                }
                start.previous_kind = row.review_kind;
            }
        }
    }
    rows.retain_mut(|row| {
        let start = &starts[&row.card_id];
        let start_id = start.latest_learning_start.unwrap_or(start.first_review_id);
        row.is_learning_start = Some(row.review_id) == start.latest_learning_start;
        row.review_id >= start_id
    });
}

#[cfg(test)]
mod tests {
    use std::path::Path;
    use std::path::PathBuf;
    use std::time::Instant;

    use rusqlite::params;
    use tempfile::tempdir;
    use tempfile::TempDir;

    use super::*;
    use crate::card::CardType;
    use crate::collection::Collection;
    use crate::collection::CollectionBuilder;

    fn temp_collection(name: &str) -> Result<(Collection, TempDir, PathBuf)> {
        let tempdir = tempdir()?;
        let col_path = tempdir.path().join(format!("{name}.anki2"));
        let mut builder = CollectionBuilder::new(&col_path);
        builder.with_desktop_media_paths();
        let col = builder.build()?;
        Ok((col, tempdir, col_path))
    }

    fn fsrs_cache_rows(count: usize, offset: i64) -> Vec<FsrsReviewRetrievabilityCacheRow> {
        (0..count)
            .map(|index| FsrsReviewRetrievabilityCacheRow {
                revlog_id: RevlogId(offset + index as i64 + 1),
                prediction: (index % 100) as f32 / 100.0,
                sample_role: FsrsReviewRetrievabilitySampleRole::FinalFit,
                fold_index: -1,
            })
            .collect()
    }

    fn count_fsrs_cache_rows(storage: &SqliteStorage) -> Result<usize> {
        storage
            .db
            .query_row(
                &format!("SELECT count() FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE}"),
                [],
                |row| row.get::<_, usize>(0),
            )
            .map_err(Into::into)
    }

    fn clear_fsrs_cache_rows(storage: &SqliteStorage) -> Result<()> {
        storage.db.execute_batch(&format!(
            "DELETE FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE};"
        ))?;
        Ok(())
    }

    fn checkpoint_truncate(storage: &SqliteStorage) -> Result<()> {
        storage
            .db
            .execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")?;
        Ok(())
    }

    fn wal_path(path: &Path) -> PathBuf {
        let mut wal = path.as_os_str().to_os_string();
        wal.push("-wal");
        wal.into()
    }

    fn file_size(path: &Path) -> u64 {
        std::fs::metadata(path)
            .map(|metadata| metadata.len())
            .unwrap_or(0)
    }

    fn sidecar_table_exists(storage: &SqliteStorage, table: &str) -> Result<bool> {
        storage
            .db
            .prepare(&format!(
                "SELECT null FROM {RETRIEVABILITY_CACHE_DB_SCHEMA}.sqlite_master \
                 WHERE type = 'table' AND name = ?"
            ))?
            .exists([table])
            .map_err(Into::into)
    }

    #[test]
    fn retrievability_cache_tables_are_external_to_collection_db() -> Result<()> {
        let (col, _tempdir, col_path) = temp_collection("external-retrievability-cache")?;
        let rows = fsrs_cache_rows(1, 0);

        col.storage
            .set_fsrs_review_retrievability_predictions(&rows, "test")?;
        col.storage
            .set_rwkv_review_retrievability_prediction(RevlogId(1), 0.42, "rwkv_review")?;

        assert!(!col
            .storage
            .main_table_exists(FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE)?);
        assert!(!col
            .storage
            .main_table_exists(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE)?);
        assert!(sidecar_table_exists(
            &col.storage,
            FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE
        )?);
        assert!(sidecar_table_exists(
            &col.storage,
            RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE
        )?);
        assert!(super::super::sqlite::retrievability_cache_path(&col_path).exists());
        assert_eq!(count_fsrs_cache_rows(&col.storage)?, 1);

        let rwkv_count: usize = col.storage.db.query_row(
            &format!("SELECT count() FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}"),
            [],
            |row| row.get(0),
        )?;
        assert_eq!(rwkv_count, 1);
        Ok(())
    }

    #[test]
    fn retrievability_cache_relocation_preserves_wal_predictions() -> Result<()> {
        let (col, source_dir, col_path) = temp_collection("collection")?;
        let cache_path = super::super::sqlite::retrievability_cache_path(&col_path);
        drop(col);
        let old_path = source_dir
            .path()
            .join("collection.retrievability-cache.sqlite");
        std::fs::rename(&cache_path, &old_path)?;
        let old_db = rusqlite::Connection::open(&old_path)?;
        old_db.execute_batch(&format!(
            "PRAGMA journal_mode = WAL; PRAGMA wal_autocheckpoint = 0;
             INSERT INTO {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} VALUES (1, 0.7, 'test', 0, 'final_fit', -1);
             INSERT INTO {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} VALUES (1, 0.6, 'rwkv_review', 0, 'post_optimization', -1);"
        ))?;
        // Snapshot a committed WAL before the source connection checkpoints it.
        let target_dir = tempdir()?;
        let target_col = target_dir.path().join("collection.anki2");
        std::fs::copy(&col_path, &target_col)?;
        let target_old = target_dir
            .path()
            .join("collection.retrievability-cache.sqlite");
        std::fs::copy(&old_path, &target_old)?;
        std::fs::copy(wal_path(&old_path), wal_path(&target_old))?;
        let mut builder = CollectionBuilder::new(&target_col);
        builder.with_desktop_media_paths();
        let col = builder.build()?;
        let target_cache = super::super::sqlite::retrievability_cache_path(&target_col);
        assert_eq!(
            target_cache,
            target_dir
                .path()
                .join("rwkv-state-cache/collection.retrievability-cache.sqlite")
        );
        assert!(target_cache.exists());
        assert!(!target_old.exists());
        assert!(!wal_path(&target_old).exists());
        assert_eq!(count_fsrs_cache_rows(&col.storage)?, 1);
        let prediction: f64 = col.storage.db.query_row(
            &format!("SELECT prediction FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}"),
            [],
            |row| row.get(0),
        )?;
        assert_eq!(prediction, 0.6);
        Ok(())
    }

    #[test]
    fn clear_review_retrievability_cache_clears_both_models() -> Result<()> {
        let (col, _dir, _path) = temp_collection("clear-cache")?;
        col.storage
            .set_fsrs_review_retrievability_predictions(&fsrs_cache_rows(2, 0), "test")?;
        col.storage
            .set_rwkv_review_retrievability_prediction(RevlogId(1), 0.7, "rwkv_review")?;
        col.storage
            .db
            .execute_batch("INSERT INTO revlog VALUES (1, 1, 0, 3, 1, 0, 2500, 1000, 1)")?;
        col.storage.clear_review_retrievability_cache()?;
        assert_eq!(count_fsrs_cache_rows(&col.storage)?, 0);
        let rwkv_count: usize = col.storage.db.query_row(
            &format!("SELECT count() FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}"),
            [],
            |row| row.get(0),
        )?;
        assert_eq!(rwkv_count, 0);
        let reviews: usize =
            col.storage
                .db
                .query_row("SELECT count() FROM revlog WHERE ease = 3", [], |row| {
                    row.get(0)
                })?;
        assert_eq!(reviews, 1);
        Ok(())
    }

    #[test]
    fn legacy_main_retrievability_cache_tables_migrate_to_sidecar() -> Result<()> {
        let (col, _tempdir, col_path) = temp_collection("migrate-retrievability-cache")?;
        col.storage.db.execute_batch(&format!(
            "
            CREATE TABLE main.{FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} (
                revlog_id INTEGER NOT NULL,
                prediction REAL NOT NULL,
                source TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sample_role TEXT NOT NULL DEFAULT 'final_fit',
                fold_index INTEGER NOT NULL DEFAULT -1,
                PRIMARY KEY (revlog_id, sample_role, fold_index, source)
            );
            INSERT INTO main.{FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                (revlog_id, prediction, source, updated_at, sample_role, fold_index)
            VALUES (1, 0.25, 'legacy_fsrs', 123, 'validation_fold', 2);
            CREATE TABLE main.{RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} (
                revlog_id INTEGER NOT NULL,
                prediction REAL NOT NULL,
                source TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                sample_role TEXT NOT NULL DEFAULT 'final_fit',
                fold_index INTEGER NOT NULL DEFAULT -1,
                PRIMARY KEY (revlog_id, sample_role, fold_index, source)
            );
            INSERT INTO main.{RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                (revlog_id, prediction, source, updated_at, sample_role, fold_index)
            VALUES (2, 0.75, 'legacy_rwkv', 456, 'test_fold', 0);
            "
        ))?;
        col.storage
            .set_schema_modified_time(TimestampMillis(1_000))?;
        col.storage.set_last_sync(TimestampMillis(1_000))?;
        col.close(None)?;

        let mut builder = CollectionBuilder::new(&col_path);
        builder.with_desktop_media_paths();
        let col = builder.build()?;

        assert!(!col
            .storage
            .main_table_exists(FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE)?);
        assert!(!col
            .storage
            .main_table_exists(RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE)?);
        assert!(col
            .storage
            .get_collection_timestamps()?
            .schema_changed_since_sync());
        assert!(col
            .storage
            .review_retrievability_cache_cleanup_full_sync_marked()?);

        let fsrs: (f64, String, i64) = col.storage.db.query_row(
            &format!(
                "SELECT prediction, sample_role, fold_index \
                 FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = 1"
            ),
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )?;
        assert_eq!(fsrs, (0.25, "validation_fold".to_string(), 2));

        let rwkv: (f64, String, i64) = col.storage.db.query_row(
            &format!(
                "SELECT prediction, sample_role, fold_index \
                 FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = 2"
            ),
            [],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
        )?;
        assert_eq!(rwkv, (0.75, "test_fold".to_string(), 0));
        Ok(())
    }

    fn legacy_autocommit_fsrs_insert(
        storage: &SqliteStorage,
        rows: &[FsrsReviewRetrievabilityCacheRow],
        source: &str,
    ) -> Result<usize> {
        storage.ensure_fsrs_review_retrievability_cache_schema()?;
        let updated_at = TimestampMillis::now().0;
        let mut stored = 0;
        let mut stmt = storage.db.prepare_cached(&format!(
            "
            INSERT OR REPLACE INTO {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                (revlog_id, prediction, source, updated_at, sample_role, fold_index)
            VALUES (?, ?, ?, ?, ?, ?)
            "
        ))?;

        for row in rows {
            if row.revlog_id.0 > 0
                && row.prediction.is_finite()
                && (0.0..=1.0).contains(&row.prediction)
            {
                stmt.execute(params![
                    row.revlog_id,
                    row.prediction,
                    source,
                    updated_at,
                    row.sample_role.as_str(),
                    row.fold_index
                ])?;
                stored += 1;
            }
        }

        Ok(stored)
    }

    #[test]
    fn fsrs_retrievability_cache_batch_works_inside_transaction() -> Result<()> {
        let (col, _tempdir, _col_path) = temp_collection("fsrs-cache-transaction")?;
        let rows = fsrs_cache_rows(3, 0);

        col.storage.begin_trx()?;
        let stored = col
            .storage
            .set_fsrs_review_retrievability_predictions(&rows, "test")?;
        col.storage.commit_trx()?;

        assert_eq!(stored, 3);
        assert_eq!(count_fsrs_cache_rows(&col.storage)?, 3);
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_cache_skips_unchanged_rebuild_rows() -> Result<()> {
        let (col, _tempdir, _col_path) = temp_collection("fsrs-cache-skip-unchanged")?;
        let mut rows = fsrs_cache_rows(1, 0);

        col.storage
            .set_fsrs_review_retrievability_predictions(&rows, "test")?;
        col.storage.db.execute(
            &format!(
                "
                UPDATE {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                SET updated_at = 123
                WHERE revlog_id = ? AND sample_role = ? AND fold_index = ? AND source = ?
                "
            ),
            params![
                rows[0].revlog_id,
                rows[0].sample_role.as_str(),
                rows[0].fold_index,
                "test",
            ],
        )?;

        assert_eq!(
            col.storage
                .set_fsrs_review_retrievability_predictions(&rows, "test")?,
            1
        );
        let unchanged_updated_at: i64 = col.storage.db.query_row(
            &format!(
                "SELECT updated_at FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = ?"
            ),
            [rows[0].revlog_id],
            |row| row.get(0),
        )?;
        assert_eq!(unchanged_updated_at, 123);

        rows[0].prediction = 0.99;
        col.storage
            .set_fsrs_review_retrievability_predictions(&rows, "test")?;
        let (changed_prediction, changed_updated_at): (f64, i64) = col.storage.db.query_row(
            &format!(
                "SELECT prediction, updated_at FROM {FSRS_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = ?"
            ),
            [rows[0].revlog_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )?;
        assert!((changed_prediction - 0.99).abs() < 1e-6);
        assert_ne!(changed_updated_at, 123);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_cache_batch_works_inside_transaction() -> Result<()> {
        let (col, _tempdir, _col_path) = temp_collection("rwkv-cache-transaction")?;
        let rows = [
            RwkvReviewRetrievabilityCacheRow {
                revlog_id: RevlogId(1),
                prediction: 0.25,
                sample_role: RwkvReviewRetrievabilitySampleRole::FinalFit,
                fold_index: -1,
            },
            RwkvReviewRetrievabilityCacheRow {
                revlog_id: RevlogId(2),
                prediction: 0.75,
                sample_role: RwkvReviewRetrievabilitySampleRole::PostOptimization,
                fold_index: -1,
            },
        ];

        col.storage.begin_trx()?;
        let stored = col
            .storage
            .set_rwkv_review_retrievability_predictions(&rows, "test")?;
        col.storage.commit_trx()?;

        let count: usize = col.storage.db.query_row(
            &format!("SELECT count() FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}"),
            [],
            |row| row.get(0),
        )?;
        assert_eq!(stored, 2);
        assert_eq!(count, 2);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_cache_skips_unchanged_rebuild_rows() -> Result<()> {
        let (col, _tempdir, _col_path) = temp_collection("rwkv-cache-skip-unchanged")?;
        let mut rows = [RwkvReviewRetrievabilityCacheRow {
            revlog_id: RevlogId(1),
            prediction: 0.25,
            sample_role: RwkvReviewRetrievabilitySampleRole::FinalFit,
            fold_index: -1,
        }];

        col.storage
            .set_rwkv_review_retrievability_predictions(&rows, "test")?;
        col.storage.db.execute(
            &format!(
                "
                UPDATE {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                SET updated_at = 123
                WHERE revlog_id = ? AND sample_role = ? AND fold_index = ? AND source = ?
                "
            ),
            params![
                rows[0].revlog_id,
                rows[0].sample_role.as_str(),
                rows[0].fold_index,
                "test",
            ],
        )?;

        assert_eq!(
            col.storage
                .set_rwkv_review_retrievability_predictions(&rows, "test")?,
            1
        );
        let unchanged_updated_at: i64 = col.storage.db.query_row(
            &format!(
                "SELECT updated_at FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = ?"
            ),
            [rows[0].revlog_id],
            |row| row.get(0),
        )?;
        assert_eq!(unchanged_updated_at, 123);

        rows[0].prediction = 0.75;
        col.storage
            .set_rwkv_review_retrievability_predictions(&rows, "test")?;
        let (changed_prediction, changed_updated_at): (f64, i64) = col.storage.db.query_row(
            &format!(
                "SELECT prediction, updated_at FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE} WHERE revlog_id = ?"
            ),
            [rows[0].revlog_id],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )?;
        assert!((changed_prediction - 0.75).abs() < 1e-6);
        assert_ne!(changed_updated_at, 123);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_prune_keeps_only_latest_run_keys() -> Result<()> {
        use RwkvReviewRetrievabilitySampleRole::*;
        let (col, _tempdir, _col_path) = temp_collection("rwkv-cache-prune")?;
        let row = |revlog_id, sample_role, fold_index| RwkvReviewRetrievabilityCacheRow {
            revlog_id: RevlogId(revlog_id),
            prediction: 0.5,
            sample_role,
            fold_index,
        };
        let storage = &col.storage;
        // Review 1 was test fold 2 in an earlier run and test fold 3 now; review
        // 2 left the validation folds; review 3 no longer exists in history.
        storage.set_rwkv_review_retrievability_predictions(
            &[
                row(1, TestFold, 2),
                row(1, TestFold, 3),
                row(2, TestFold, 1),
            ],
            "rwkv_calibration_recompute",
        )?;
        storage.set_rwkv_review_retrievability_predictions(
            &[row(2, FinalFit, -1), row(3, FinalFit, -1)],
            "rwkv_calibration_recompute",
        )?;
        storage.set_rwkv_review_retrievability_predictions(
            &[row(2, FinalFit, -1)],
            "rwkv_calibration_train",
        )?;
        storage.set_rwkv_review_retrievability_predictions(
            &[row(1, PostOptimization, -1)],
            "rwkv_review",
        )?;
        storage.set_rwkv_review_retrievability_predictions(
            &[row(1, FinalFit, -1)],
            "rwkv_state_cache_build",
        )?;
        let superseded = vec!["rwkv_calibration_train".to_string()];
        let remaining = || -> Result<Vec<(i64, String, i32, String)>> {
            let mut stmt = storage.db.prepare(&format!(
                "SELECT revlog_id, sample_role, fold_index, source
                 FROM {RWKV_REVIEW_RETRIEVABILITY_CACHE_TABLE}
                 ORDER BY revlog_id, source, sample_role, fold_index"
            ))?;
            let rows = stmt
                .query_map([], |row| {
                    Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?))
                })?
                .collect::<rusqlite::Result<Vec<_>>>()?;
            Ok(rows)
        };

        // An empty run supersedes nothing.
        assert_eq!(
            storage.prune_rwkv_review_retrievability_rows(
                "rwkv_calibration_recompute",
                &[],
                &superseded
            )?,
            0
        );
        assert_eq!(remaining()?.len(), 8);

        let deleted = storage.prune_rwkv_review_retrievability_rows(
            "rwkv_calibration_recompute",
            &[(RevlogId(1), TestFold, 3), (RevlogId(2), FinalFit, -1)],
            &superseded,
        )?;
        assert_eq!(deleted, 4);
        let as_owned = |id: i64, role: &str, fold: i32, source: &str| {
            (id, role.to_string(), fold, source.to_string())
        };
        assert_eq!(
            remaining()?,
            vec![
                as_owned(1, "test_fold", 3, "rwkv_calibration_recompute"),
                as_owned(1, "post_optimization", -1, "rwkv_review"),
                as_owned(1, "final_fit", -1, "rwkv_state_cache_build"),
                as_owned(2, "final_fit", -1, "rwkv_calibration_recompute"),
            ]
        );
        Ok(())
    }

    #[test]
    #[ignore]
    fn retrievability_cache_insert_benchmark() -> Result<()> {
        let row_count = std::env::var("ANKI_RETRIEVABILITY_CACHE_BENCH_ROWS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .unwrap_or(10_000);
        let repeated_runs = std::env::var("ANKI_RETRIEVABILITY_CACHE_BENCH_REPEATS")
            .ok()
            .and_then(|value| value.parse::<usize>().ok())
            .unwrap_or(3);
        let (col, _tempdir, col_path) =
            if let Ok(source) = std::env::var("ANKI_RETRIEVABILITY_CACHE_BENCH_COLLECTION") {
                let tempdir = tempdir()?;
                let col_path = tempdir.path().join("bench.anki2");
                std::fs::copy(source, &col_path)?;
                let mut builder = CollectionBuilder::new(&col_path);
                builder.with_desktop_media_paths();
                (builder.build()?, tempdir, col_path)
            } else {
                temp_collection("retrievability-cache-bench")?
            };
        let wal_path = wal_path(&col_path);
        let rows = fsrs_cache_rows(row_count, 1_000_000_000);

        col.storage
            .set_fsrs_review_retrievability_predictions(&rows[0..1], "setup")?;
        clear_fsrs_cache_rows(&col.storage)?;
        checkpoint_truncate(&col.storage)?;

        let legacy_started = Instant::now();
        let legacy_stored = legacy_autocommit_fsrs_insert(&col.storage, &rows, "legacy")?;
        let legacy_elapsed = legacy_started.elapsed();
        let legacy_wal_bytes = file_size(&wal_path);
        clear_fsrs_cache_rows(&col.storage)?;
        checkpoint_truncate(&col.storage)?;

        let batched_started = Instant::now();
        let batched_stored = col
            .storage
            .set_fsrs_review_retrievability_predictions(&rows, "batched")?;
        let batched_elapsed = batched_started.elapsed();
        let batched_wal_bytes = file_size(&wal_path);

        let mut repeated_stored = 0;
        let mut repeated_total_ms = 0.0;
        let mut repeated_wal_bytes = 0;
        for _ in 0..repeated_runs {
            checkpoint_truncate(&col.storage)?;
            let repeated_started = Instant::now();
            repeated_stored += col
                .storage
                .set_fsrs_review_retrievability_predictions(&rows, "batched")?;
            repeated_total_ms += repeated_started.elapsed().as_secs_f64() * 1000.0;
            repeated_wal_bytes += file_size(&wal_path);
        }

        println!(
            "retrievability_cache_insert_benchmark rows={row_count} repeated_runs={repeated_runs} \
             legacy_stored={legacy_stored} legacy_ms={:.3} legacy_wal_bytes={} \
             batched_stored={batched_stored} batched_ms={:.3} batched_wal_bytes={} \
             repeated_stored={repeated_stored} repeated_total_ms={repeated_total_ms:.3} \
             repeated_wal_bytes={repeated_wal_bytes}",
            legacy_elapsed.as_secs_f64() * 1000.0,
            legacy_wal_bytes,
            batched_elapsed.as_secs_f64() * 1000.0,
            batched_wal_bytes,
        );

        assert_eq!(legacy_stored, row_count);
        assert_eq!(batched_stored, row_count);
        assert_eq!(repeated_stored, row_count * repeated_runs);
        Ok(())
    }

    #[test]
    #[ignore]
    fn times_of_last_review_benchmark() -> Result<()> {
        let card_count: usize = std::env::var("ANKI_TIMES_BENCH_CARDS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(2048);
        let reviews_per_card: usize = std::env::var("ANKI_TIMES_BENCH_REVIEWS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(20);
        let repeats: usize = std::env::var("ANKI_TIMES_BENCH_REPEATS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(5);

        let (col, _tempdir, _col_path) = if let Ok(source) =
            std::env::var("ANKI_TIMES_BENCH_COLLECTION")
        {
            let tempdir = tempdir()?;
            let col_path = tempdir.path().join("bench.anki2");
            std::fs::copy(&source, &col_path)?;
            let mut builder = CollectionBuilder::new(&col_path);
            builder.with_desktop_media_paths();
            (builder.build()?, tempdir, col_path)
        } else {
            let (col, tempdir, col_path) = temp_collection("times-bench")?;
            let base_ts: i64 = 1_700_000_000_000;
            col.storage.db.execute_batch("BEGIN")?;
            for card_idx in 0..card_count {
                let card_id = (card_idx + 1) as i64;
                col.storage.db.execute(
                    "INSERT INTO cards (id, nid, did, ord, mod, usn, type, queue, due, ivl, \
                         factor, reps, lapses, left, odue, odid, flags, data) \
                         VALUES (?1, 1, 1, 0, 0, 0, 2, 2, 0, 30, 2500, ?2, 0, 0, 0, 0, 0, '')",
                    params![card_id, reviews_per_card],
                )?;
                for rev_idx in 0..reviews_per_card {
                    let revlog_id = base_ts + (card_idx * reviews_per_card + rev_idx) as i64;
                    col.storage.db.execute(
                            "INSERT INTO revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) \
                             VALUES (?1, ?2, 0, 2, 30, 1, 2500, 10000, 1)",
                            params![revlog_id, card_id],
                        )?;
                }
            }
            col.storage.db.execute_batch("COMMIT")?;
            (col, tempdir, col_path)
        };

        let card_ids: Vec<CardId> = if std::env::var("ANKI_TIMES_BENCH_COLLECTION").is_ok() {
            let mut stmt = col
                .storage
                .db
                .prepare("SELECT id FROM cards WHERE queue = 2 LIMIT ?1")?;
            let ids: Vec<CardId> = stmt
                .query_map([card_count as i64], |row| row.get(0))?
                .collect::<std::result::Result<Vec<_>, _>>()?;
            ids
        } else {
            (1..=card_count as i64).map(CardId).collect()
        };

        println!(
            "times_of_last_review_benchmark: cards={} reviews_per_card={} repeats={}",
            card_ids.len(),
            reviews_per_card,
            repeats,
        );

        // Benchmark: new GROUP BY approach (current)
        let mut group_by_total_ms = 0.0;
        let mut group_by_result = HashMap::new();
        for _ in 0..repeats {
            let start = Instant::now();
            group_by_result = col.storage.times_of_last_review(&card_ids)?;
            group_by_total_ms += start.elapsed().as_secs_f64() * 1000.0;
        }

        // Benchmark: old correlated subquery approach
        let mut correlated_total_ms = 0.0;
        let mut correlated_result = HashMap::new();
        for _ in 0..repeats {
            let start = Instant::now();
            correlated_result = times_of_last_review_correlated(&col.storage, &card_ids)?;
            correlated_total_ms += start.elapsed().as_secs_f64() * 1000.0;
        }

        println!(
            "  GROUP BY:   total={:.1}ms avg={:.2}ms results={}",
            group_by_total_ms,
            group_by_total_ms / repeats as f64,
            group_by_result.len(),
        );
        println!(
            "  CORRELATED: total={:.1}ms avg={:.2}ms results={}",
            correlated_total_ms,
            correlated_total_ms / repeats as f64,
            correlated_result.len(),
        );
        println!(
            "  Speedup: {:.1}x",
            correlated_total_ms / group_by_total_ms.max(0.001),
        );

        assert_eq!(group_by_result.len(), correlated_result.len());
        for (card_id, group_by_time) in &group_by_result {
            assert_eq!(
                correlated_result.get(card_id),
                Some(group_by_time),
                "mismatch for card_id={card_id:?}",
            );
        }
        Ok(())
    }

    fn times_of_last_review_correlated(
        storage: &SqliteStorage,
        card_ids: &[CardId],
    ) -> Result<HashMap<CardId, TimestampSecs>> {
        if card_ids.is_empty() {
            return Ok(HashMap::new());
        }
        let mut ids = String::new();
        ids_to_string(&mut ids, card_ids);
        let sql = format!(
            "select id, (select revlog.id / 1000 from revlog \
             where cid = cards.id and ease between 1 and 4 \
             and (type != 3 or factor != 0) \
             order by revlog.id desc limit 1) from cards where id in {ids}"
        );
        let mut review_times = HashMap::new();
        let mut stmt = storage.db.prepare(&sql)?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let cid: CardId = row.get(0)?;
            let last_review_time: Option<TimestampSecs> = row.get(1)?;
            if let Some(time) = last_review_time {
                review_times.insert(cid, time);
            }
        }
        Ok(review_times)
    }

    #[test]
    fn rwkv_historical_rows_preserve_learning_start_at_cutoff() {
        for (cutoff, expected_ids, expected_starts) in [
            (None, vec![3, 5, 6, 7, 8], vec![3, 5]),
            (Some((1, 2)), vec![2, 3, 4, 5, 6, 7, 8], vec![2, 3]),
            (Some((1, 5)), vec![3, 5, 6, 7, 8], vec![3, 5]),
            (Some((1, 0)), vec![1, 2, 3, 4, 5, 6, 7, 8], vec![3]),
            // A card whose first review is a preserved learning review keeps
            // its history without marking that review as a learning start.
            (Some((2, 0)), vec![3, 5, 6, 7, 8], vec![5]),
        ] {
            let mut rows = [
                (1, 1, 1),
                (2, 1, 0),
                (3, 2, 0),
                (4, 1, 1),
                (5, 1, 0),
                (6, 2, 1),
                (7, 1, 0),
                (8, 1, 1),
            ]
            .into_iter()
            .map(
                |(review_id, card_id, review_kind)| RwkvHistoricalReviewRow {
                    review_id,
                    card_id,
                    review_kind,
                    note_id: card_id,
                    deck_id: 1,
                    ease: 3,
                    duration_millis: 1_000,
                    interval_days: 1,
                    ease_factor: 2_500,
                    is_learning_start: false,
                },
            )
            .collect::<Vec<_>>();
            let cutoffs = cutoff.into_iter().collect();

            retain_rwkv_rows_from_latest_learning_start(&mut rows, &cutoffs);

            assert_eq!(
                rows.iter().map(|row| row.review_id).collect::<Vec<_>>(),
                expected_ids,
                "cutoff: {cutoff:?}"
            );
            assert_eq!(
                rows.iter()
                    .filter(|row| row.is_learning_start)
                    .map(|row| row.review_id)
                    .collect::<Vec<_>>(),
                expected_starts,
                "cutoff: {cutoff:?}"
            );
        }
    }

    /// The window-function query `rwkv_historical_review_rows` used before the
    /// replay start moved into Rust; kept as the parity reference.
    fn reference_rwkv_historical_review_rows(
        storage: &SqliteStorage,
        ignored_clause: &str,
    ) -> Result<Vec<RwkvHistoricalReviewRow>> {
        let sql = format!(
            "
with latest_resets as (
  select cid, max(id) as reset_id
  from revlog
  where type = 4 and ease = 0 and factor = 0
  group by cid
), eligible as (
  select
    r.id, r.cid, c.nid,
    case when c.odid != 0 then c.odid else c.did end as deck_id,
    r.ease, r.time, r.type,
    cast(r.ivl as integer) as interval_days,
    cast(r.factor as integer) as ease_factor,
    lag(r.type) over (partition by r.cid order by r.id) as previous_type
  from revlog r
  join cards c on c.id = r.cid
  left join latest_resets resets on resets.cid = r.cid
  where r.ease between 1 and 4
    and r.type in (0, 1, 2, 3, 4, 5)
    and not (r.type = 3 and r.factor = 0)
    and c.type != 0
    and r.id > coalesce(resets.reset_id, 0)
    and r.id > ({RWKV_HISTORY_RESET_AFTER_SQL})
    {ignored_clause}
), pending_restarts as (
  select e.cid
  from eligible e
  join cards c on c.id = e.cid
  where c.type = 0
), retained_starts as (
  select
    cid,
    coalesce(
      max(case when type = 0 and (previous_type is null or previous_type != 0) then id end),
      min(id)
    ) as start_id
  from eligible
  where cid not in (select cid from pending_restarts)
  group by cid
)
select e.id, e.cid, e.nid, e.deck_id, e.ease, e.time, e.type, e.interval_days,
  e.ease_factor, e.id = s.start_id and e.type = 0
from eligible e
join retained_starts s on s.cid = e.cid
where e.id >= s.start_id
order by e.id, e.cid"
        );
        storage
            .db
            .prepare(&sql)?
            .query_map([], |row| {
                Ok(RwkvHistoricalReviewRow {
                    review_id: row.get(0)?,
                    card_id: row.get(1)?,
                    note_id: row.get(2)?,
                    deck_id: row.get(3)?,
                    ease: row.get(4)?,
                    duration_millis: row.get(5)?,
                    review_kind: row.get(6)?,
                    interval_days: row.get(7)?,
                    ease_factor: row.get(8)?,
                    is_learning_start: row.get(9)?,
                })
            })?
            .collect::<std::result::Result<Vec<_>, _>>()
            .map_err(Into::into)
    }

    #[test]
    fn rwkv_historical_review_rows_match_window_query() -> Result<()> {
        let (mut col, _tempdir, _col_path) = temp_collection("rwkv-historical-rows")?;
        let mut seed = 0x2545_f491_4f6c_dd1d_u64;
        let mut next = |bound: u64| {
            seed ^= seed << 13;
            seed ^= seed >> 7;
            seed ^= seed << 17;
            seed % bound
        };
        let mut card_ids = Vec::new();
        col.transact_no_undo(|col| {
            for card_index in 0..40_i64 {
                let mut card = Card::new(
                    NoteId(1_000 + card_index / 3),
                    0,
                    DeckId(1 + card_index % 4),
                    0,
                );
                card.ctype = if card_index % 2 == 0 {
                    CardType::Review
                } else {
                    CardType::New
                };
                card.original_deck_id = DeckId(if card_index % 7 == 0 { 9 } else { 0 });
                card.rwkv_history_reset_after = (card_index % 4 == 0).then_some(13_000);
                col.add_card(&mut card)?;
                if card_index % 8 == 0 {
                    let mut mirrored = card.clone();
                    mirrored.rwkv_history_reset_after = Some(14_000);
                    col.mirror_rwkv_history_reset(&mirrored)?;
                }
                card_ids.push(card.id.0);
            }
            Ok(())
        })?;
        // Card 5000 has reviews but no card row, so the join drops them.
        card_ids.push(5_000);
        let mut review_ids = Vec::new();
        col.transact_no_undo(|col| {
            for review_index in 0..2_000_i64 {
                let review_id = 10_000 + review_index * 3 + next(3) as i64;
                let card_id = card_ids[next(card_ids.len() as u64) as usize];
                // Bias towards learning rows so cards restart learning often.
                let kind = [
                    RevlogReviewKind::Learning,
                    RevlogReviewKind::Learning,
                    RevlogReviewKind::Learning,
                    RevlogReviewKind::Review,
                    RevlogReviewKind::Review,
                    RevlogReviewKind::Relearning,
                    RevlogReviewKind::Filtered,
                    RevlogReviewKind::Manual,
                    RevlogReviewKind::Rescheduled,
                ][next(9) as usize];
                let ease = next(6) as u8;
                let factor = if next(5) == 0 { 0 } else { 2_500 };
                let id = col.add_revlog_entry_undoable(RevlogEntry {
                    id: RevlogId(review_id),
                    cid: CardId(card_id),
                    button_chosen: ease,
                    interval: next(40) as i32 - 10,
                    ease_factor: factor,
                    taken_millis: next(60_000) as u32,
                    review_kind: kind,
                    ..Default::default()
                })?;
                review_ids.push(id.0);
            }
            Ok(())
        })?;

        let (rows, active) = col.storage.rwkv_historical_review_rows(&[])?;
        assert!(active.is_empty());
        assert!(rows.iter().any(|row| row.is_learning_start));
        assert!(rows
            .iter()
            .all(|row| row.card_id % 4 != 0 || row.review_id > 13_000));
        let eligible: usize = col.storage.db.query_row(
            "select count() from revlog r join cards c on c.id = r.cid
             where r.ease between 1 and 4 and r.type in (0, 1, 2, 3, 4, 5)
               and not (r.type = 3 and r.factor = 0)",
            [],
            |row| row.get(0),
        )?;
        assert!(
            rows.len() < eligible,
            "some pre-restart reviews are dropped"
        );
        assert_eq!(
            rows,
            reference_rwkv_historical_review_rows(&col.storage, "")?
        );

        let ignored = review_ids
            .iter()
            .step_by(37)
            .map(|id| RevlogId(*id))
            .collect::<Vec<_>>();
        let mut ids = String::new();
        ids_to_string(&mut ids, &ignored);
        let (rows, _active) = col.storage.rwkv_historical_review_rows(&ignored)?;
        assert_eq!(
            rows,
            reference_rwkv_historical_review_rows(&col.storage, &format!("and r.id not in {ids}"))?
        );
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use std::path::Path;

    use anki_i18n::I18n;

    use super::*;
    use crate::card::Card;

    fn create_test_storage() -> SqliteStorage {
        SqliteStorage::open_or_create(Path::new(":memory:"), &I18n::template_only(), false, false)
            .unwrap()
    }

    /// A revlog entry with a fixed card id, used where the card is irrelevant.
    fn revlog(id: i64, kind: RevlogReviewKind, taken_millis: u32) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(id),
            cid: CardId(1),
            button_chosen: 3,
            taken_millis,
            review_kind: kind,
            ..Default::default()
        }
    }

    fn review_for_card(id: i64, cid: CardId, button_chosen: u8) -> RevlogEntry {
        RevlogEntry {
            id: RevlogId(id),
            cid,
            button_chosen,
            review_kind: RevlogReviewKind::Review,
            ..Default::default()
        }
    }

    #[test]
    fn add_revlog_entry_returns_new_id_and_persists_unique_entry() {
        let storage = create_test_storage();
        let entry = revlog(1000, RevlogReviewKind::Review, 42);

        let added = storage.add_revlog_entry(&entry, false).unwrap();

        assert_eq!(added, Some(RevlogId(1000)));
        assert_eq!(
            storage.get_revlog_entry(RevlogId(1000)).unwrap(),
            Some(entry)
        );
    }

    #[test]
    fn add_revlog_entry_reassigns_id_on_collision_when_uniquify() {
        let storage = create_test_storage();
        storage
            .add_revlog_entry(&revlog(1000, RevlogReviewKind::Review, 0), false)
            .unwrap();

        let added = storage
            .add_revlog_entry(&revlog(1000, RevlogReviewKind::Review, 0), true)
            .unwrap();

        // uniquify picks max(id)+1, so the option is safe to unwrap
        assert_eq!(added, Some(RevlogId(1001)));
        assert!(storage.get_revlog_entry(RevlogId(1000)).unwrap().is_some());
        assert!(storage.get_revlog_entry(RevlogId(1001)).unwrap().is_some());
    }

    #[test]
    fn add_revlog_entry_returns_none_on_collision_without_uniquify() {
        let storage = create_test_storage();
        let original = revlog(1000, RevlogReviewKind::Review, 5);
        storage.add_revlog_entry(&original, false).unwrap();

        let dup = revlog(1000, RevlogReviewKind::Manual, 9);
        let added = storage.add_revlog_entry(&dup, false).unwrap();

        assert_eq!(added, None);
        // the original row must be left untouched (INSERT OR IGNORE)
        assert_eq!(
            storage.get_revlog_entry(RevlogId(1000)).unwrap(),
            Some(original)
        );
    }

    #[test]
    fn get_revlog_entries_for_card_returns_only_matching_entries() {
        let storage = create_test_storage();
        storage
            .add_revlog_entry(&review_for_card(1, CardId(10), 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(2, CardId(10), 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(3, CardId(20), 3), false)
            .unwrap();

        let for_card = storage.get_revlog_entries_for_card(CardId(10)).unwrap();

        assert_eq!(for_card.len(), 2);
        assert!(for_card.iter().all(|e| e.cid == CardId(10)));
        assert!(storage
            .get_revlog_entries_for_card(CardId(999))
            .unwrap()
            .is_empty());
    }

    #[test]
    fn unknown_review_kind_falls_back_to_default() {
        let storage = create_test_storage();
        // A future/unknown review kind must not error when read back.
        storage
            .db
            .execute(
                "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) \
                 values (?,?,?,?,?,?,?,?,?)",
                params![100i64, 1i64, 0, 3, 0, 0, 0, 0, 99],
            )
            .unwrap();

        let entry = storage.get_revlog_entry(RevlogId(100)).unwrap().unwrap();

        assert_eq!(entry.review_kind, RevlogReviewKind::default());
    }

    #[test]
    fn time_of_last_review_returns_most_recent_qualifying_review() {
        let storage = create_test_storage();
        let cid = CardId(42);
        storage
            .add_revlog_entry(&review_for_card(1_000_000, cid, 3), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(3_000_000, cid, 2), false)
            .unwrap();
        storage
            .add_revlog_entry(&review_for_card(2_000_000, cid, 4), false)
            .unwrap();

        let last = storage.time_of_last_review(cid).unwrap();

        // id is in millis; the stored review time is id / 1000
        assert_eq!(last, Some(TimestampSecs(3_000)));
    }

    #[test]
    fn time_of_last_review_ignores_entries_without_a_rating() {
        let storage = create_test_storage();
        let cid = CardId(7);
        // a reset entry has ease 0, so it is not a real review
        let reset = RevlogEntry {
            id: RevlogId(5_000_000),
            cid,
            button_chosen: 0,
            review_kind: RevlogReviewKind::Manual,
            ..Default::default()
        };
        storage.add_revlog_entry(&reset, false).unwrap();

        assert_eq!(storage.time_of_last_review(cid).unwrap(), None);
        assert_eq!(storage.time_of_last_review(CardId(999)).unwrap(), None);
    }

    #[test]
    fn studied_today_counts_reviews_in_window_excluding_manual_and_rescheduled() {
        let storage = create_test_storage();
        let day_cutoff = TimestampSecs(1_000_000);
        // window start (in millis) is (1_000_000 - 86_400) * 1000 = 913_600_000
        storage
            .add_revlog_entry(&revlog(950_000_000, RevlogReviewKind::Review, 5000), false)
            .unwrap();
        storage
            .add_revlog_entry(
                &revlog(950_000_001, RevlogReviewKind::Learning, 3000),
                false,
            )
            .unwrap();
        // excluded by kind
        storage
            .add_revlog_entry(&revlog(950_000_002, RevlogReviewKind::Manual, 9999), false)
            .unwrap();
        storage
            .add_revlog_entry(
                &revlog(950_000_003, RevlogReviewKind::Rescheduled, 9999),
                false,
            )
            .unwrap();
        // excluded by being before the window
        storage
            .add_revlog_entry(&revlog(900_000_000, RevlogReviewKind::Review, 9999), false)
            .unwrap();

        let studied = storage.studied_today(day_cutoff).unwrap();

        assert_eq!(studied.cards, 2);
        assert_eq!(studied.seconds, 8.0);
    }

    #[test]
    fn studied_today_returns_zero_for_empty_revlog() {
        let storage = create_test_storage();

        let studied = storage.studied_today(TimestampSecs(1_000_000)).unwrap();

        assert_eq!(studied.cards, 0);
        assert_eq!(studied.seconds, 0.0);
    }

    #[test]
    fn studied_today_by_deck_groups_by_home_deck_and_skips_unrated() {
        let mut col = Collection::new();
        let mut in_deck_100 = Card::default();
        let mut in_deck_200 = Card::default();
        let mut filtered_from_999 = Card::default();
        col.add_card(&mut in_deck_100).unwrap();
        col.add_card(&mut in_deck_200).unwrap();
        col.add_card(&mut filtered_from_999).unwrap();
        col.storage
            .db
            .execute(
                "update cards set did = 100, odid = 0 where id = ?",
                params![in_deck_100.id],
            )
            .unwrap();
        col.storage
            .db
            .execute(
                "update cards set did = 200, odid = 0 where id = ?",
                params![in_deck_200.id],
            )
            .unwrap();
        // a card currently in a filtered deck reports its original deck (odid)
        col.storage
            .db
            .execute(
                "update cards set did = 5, odid = 999 where id = ?",
                params![filtered_from_999.id],
            )
            .unwrap();

        let add = |id: i64, cid: CardId, button: u8| {
            col.storage
                .add_revlog_entry(&review_for_card(id, cid, button), false)
                .unwrap();
        };
        add(950_000_000, in_deck_100.id, 3);
        add(950_000_010, in_deck_200.id, 3);
        add(950_000_020, filtered_from_999.id, 3);
        // Repeated reviews still count as one studied card.
        add(950_000_025, in_deck_100.id, 3);
        // ease 0 is filtered out by the query
        add(950_000_030, in_deck_100.id, 0);

        let mut by_deck: Vec<(i64, usize)> = col
            .storage
            .studied_today_by_deck(TimestampSecs(1_000_000))
            .unwrap()
            .into_iter()
            .map(|(deck, count)| (deck.0, count))
            .collect();
        by_deck.sort();

        assert_eq!(by_deck, vec![(100, 1), (200, 1), (999, 1)]);
    }

    #[test]
    fn studied_today_by_deck_counts_only_cards_reviewed_after_day_start() {
        let storage = create_test_storage();
        let day_cutoff = TimestampSecs(1_000_000);
        let start = 913_600_000;
        for (id, stamp) in [(1, start - 1), (2, start), (3, start + 1)] {
            let card = Card {
                id: CardId(id),
                deck_id: DeckId(id),
                ..Default::default()
            };
            storage.add_card_if_unique(&card).unwrap();
            storage
                .add_revlog_entry(&review_for_card(stamp, card.id, 3), false)
                .unwrap();
        }

        assert_eq!(
            storage.studied_today_by_deck(day_cutoff).unwrap(),
            vec![(DeckId(3), 1)]
        );
    }

    #[test]
    fn studied_today_by_deck_requires_rating_and_review_kind_or_factor() {
        let cases = [
            (RevlogReviewKind::Learning, 3, 0, true),
            (RevlogReviewKind::Review, 3, 0, true),
            (RevlogReviewKind::Relearning, 3, 0, true),
            (RevlogReviewKind::Filtered, 3, 0, false),
            (RevlogReviewKind::Filtered, 3, 2500, true),
            (RevlogReviewKind::Manual, 3, 0, false),
            (RevlogReviewKind::Rescheduled, 3, 0, false),
            (RevlogReviewKind::Review, 0, 2500, false),
        ];
        for (kind, button, factor, counted) in cases {
            let storage = create_test_storage();
            let card = Card {
                id: CardId(1),
                ..Default::default()
            };
            storage.add_card_if_unique(&card).unwrap();
            let entry = RevlogEntry {
                review_kind: kind,
                ease_factor: factor,
                ..review_for_card(950_000_000, card.id, button)
            };
            storage.add_revlog_entry(&entry, false).unwrap();

            let expected = if counted {
                vec![(card.deck_id, 1)]
            } else {
                vec![]
            };
            assert_eq!(
                storage
                    .studied_today_by_deck(TimestampSecs(1_000_000))
                    .unwrap(),
                expected,
                "kind {kind:?}, button {button}, factor {factor}"
            );
        }
    }

    #[test]
    fn revlog_review_kind_from_sql_rejects_invalid_values() {
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Integer(1)),
            Ok(RevlogReviewKind::Review)
        ));
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Integer(99)),
            Err(FromSqlError::InvalidType)
        ));
        assert!(matches!(
            <RevlogReviewKind as FromSql>::column_result(ValueRef::Text(b"x")),
            Err(FromSqlError::InvalidType)
        ));
    }
}
