// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::ankidroid::sql_value::Data;
use anki_proto::ankidroid::DbResponse;
use anki_proto::ankidroid::DbResult as ProtoDbResult;
use anki_proto::ankidroid::SqlValue as pb_SqlValue;
use rusqlite::params_from_iter;
use rusqlite::types::FromSql;
use rusqlite::types::FromSqlError;
use rusqlite::types::ToSql;
use rusqlite::types::ToSqlOutput;
use rusqlite::types::ValueRef;
use rusqlite::OptionalExtension;
use serde::Deserialize;
use serde::Serialize;

use crate::ankidroid::db::next_sequence_number;
use crate::ankidroid::db::trim_and_cache_remaining;
use crate::prelude::*;
use crate::storage::SqliteStorage;

#[derive(Deserialize)]
#[serde(tag = "kind", rename_all = "lowercase")]
pub(super) enum DbRequest {
    Query {
        sql: String,
        args: Vec<SqlValue>,
        first_row_only: bool,
    },
    Begin,
    Commit,
    Rollback,
    ExecuteMany {
        sql: String,
        args: Vec<Vec<SqlValue>>,
    },
}

#[derive(Serialize)]
#[serde(untagged)]
pub(super) enum DbResult {
    Rows(Vec<Vec<SqlValue>>),
    None,
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(untagged)]
pub(crate) enum SqlValue {
    Null,
    String(String),
    Int(i64),
    Double(f64),
    Blob(Vec<u8>),
}

impl ToSql for SqlValue {
    fn to_sql(&self) -> std::result::Result<ToSqlOutput<'_>, rusqlite::Error> {
        let val = match self {
            SqlValue::Null => ValueRef::Null,
            SqlValue::String(v) => ValueRef::Text(v.as_bytes()),
            SqlValue::Int(v) => ValueRef::Integer(*v),
            SqlValue::Double(v) => ValueRef::Real(*v),
            SqlValue::Blob(v) => ValueRef::Blob(v),
        };
        Ok(ToSqlOutput::Borrowed(val))
    }
}

impl From<&SqlValue> for anki_proto::ankidroid::SqlValue {
    fn from(item: &SqlValue) -> Self {
        match item {
            SqlValue::Null => pb_SqlValue { data: Option::None },
            SqlValue::String(s) => pb_SqlValue {
                data: Some(Data::StringValue(s.to_string())),
            },
            SqlValue::Int(i) => pb_SqlValue {
                data: Some(Data::LongValue(*i)),
            },
            SqlValue::Double(d) => pb_SqlValue {
                data: Some(Data::DoubleValue(*d)),
            },
            SqlValue::Blob(b) => pb_SqlValue {
                data: Some(Data::BlobValue(b.clone())),
            },
        }
    }
}

fn row_to_proto(row: &[SqlValue]) -> anki_proto::ankidroid::Row {
    anki_proto::ankidroid::Row {
        fields: row
            .iter()
            .map(anki_proto::ankidroid::SqlValue::from)
            .collect(),
    }
}

fn rows_to_proto(rows: &[Vec<SqlValue>]) -> anki_proto::ankidroid::DbResult {
    anki_proto::ankidroid::DbResult {
        rows: rows.iter().map(|r| row_to_proto(r)).collect(),
    }
}

impl FromSql for SqlValue {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        let val = match value {
            ValueRef::Null => SqlValue::Null,
            ValueRef::Integer(i) => SqlValue::Int(i),
            ValueRef::Real(v) => SqlValue::Double(v),
            ValueRef::Text(v) => SqlValue::String(String::from_utf8_lossy(v).to_string()),
            ValueRef::Blob(v) => SqlValue::Blob(v.to_vec()),
        };
        Ok(val)
    }
}

pub(crate) fn db_command_bytes(col: &mut Collection, input: &[u8]) -> Result<Vec<u8>> {
    serde_json::to_vec(&db_command_bytes_inner(col, input)?).map_err(Into::into)
}

pub(super) fn db_command_bytes_inner(col: &mut Collection, input: &[u8]) -> Result<DbResult> {
    let req: DbRequest = serde_json::from_slice(input)?;
    let resp = match req {
        DbRequest::Query {
            sql,
            args,
            first_row_only,
        } => {
            update_state_after_modification(col, &sql);
            if first_row_only {
                db_query_row(&col.storage, &sql, &args)?
            } else {
                db_query(&col.storage, &sql, &args)?
            }
        }
        DbRequest::Begin => {
            col.storage.begin_trx()?;
            DbResult::None
        }
        DbRequest::Commit => {
            if col.state.modified_by_dbproxy {
                col.storage.set_modified_time(TimestampMillis::now())?;
                col.state.modified_by_dbproxy = false;
            }
            col.storage.commit_trx()?;
            DbResult::None
        }
        DbRequest::Rollback => {
            col.clear_caches();
            col.storage.rollback_trx()?;
            DbResult::None
        }
        DbRequest::ExecuteMany { sql, args } => {
            update_state_after_modification(col, &sql);
            db_execute_many(&col.storage, &sql, &args)?
        }
    };
    Ok(resp)
}

fn update_state_after_modification(col: &mut Collection, sql: &str) {
    if !is_dql(sql) {
        // println!("clearing undo+study due to {}", sql);
        col.update_state_after_dbproxy_modification();
    }
}

/// Anything other than a select statement is false.
fn is_dql(sql: &str) -> bool {
    let head: String = sql
        .trim_start()
        .chars()
        .take(10)
        .map(|c| c.to_ascii_lowercase())
        .collect();
    head.starts_with("select")
}

pub(crate) fn db_command_proto(col: &mut Collection, input: &[u8]) -> Result<DbResponse> {
    let result = db_command_bytes_inner(col, input)?;
    let proto_resp = match result {
        DbResult::None => ProtoDbResult { rows: Vec::new() },
        DbResult::Rows(rows) => rows_to_proto(&rows),
    };
    let trimmed = trim_and_cache_remaining(col, proto_resp, next_sequence_number());
    Ok(trimmed)
}

pub(super) fn db_query_row(ctx: &SqliteStorage, sql: &str, args: &[SqlValue]) -> Result<DbResult> {
    let mut stmt = ctx.db.prepare_cached(sql)?;
    let columns = stmt.column_count();

    let row = stmt
        .query_row(params_from_iter(args), |row| {
            let mut orow = Vec::with_capacity(columns);
            for i in 0..columns {
                let v: SqlValue = row.get(i)?;
                orow.push(v);
            }
            Ok(orow)
        })
        .optional()?;

    let rows = if let Some(row) = row {
        vec![row]
    } else {
        vec![]
    };

    Ok(DbResult::Rows(rows))
}

pub(super) fn db_query(ctx: &SqliteStorage, sql: &str, args: &[SqlValue]) -> Result<DbResult> {
    let mut stmt = ctx.db.prepare_cached(sql)?;
    let columns = stmt.column_count();

    let res: std::result::Result<Vec<Vec<_>>, rusqlite::Error> = stmt
        .query_map(params_from_iter(args), |row| {
            let mut orow = Vec::with_capacity(columns);
            for i in 0..columns {
                let v: SqlValue = row.get(i)?;
                orow.push(v);
            }
            Ok(orow)
        })?
        .collect();

    Ok(DbResult::Rows(res?))
}

pub(super) fn db_execute_many(
    ctx: &SqliteStorage,
    sql: &str,
    args: &[Vec<SqlValue>],
) -> Result<DbResult> {
    let mut stmt = ctx.db.prepare_cached(sql)?;

    for params in args {
        stmt.execute(params_from_iter(params))?;
    }

    Ok(DbResult::None)
}
