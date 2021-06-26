// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::{
    params_from_iter,
    types::{FromSql, FromSqlError, ToSql, ToSqlOutput, ValueRef},
    OptionalExtension,
};
use serde_derive::{Deserialize, Serialize};

use crate::{prelude::*, storage::SqliteStorage};

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
pub(super) enum SqlValue {
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

pub(super) fn db_command_bytes(col: &mut Collection, input: &[u8]) -> Result<Vec<u8>> {
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
    Ok(serde_json::to_vec(&resp)?)
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
