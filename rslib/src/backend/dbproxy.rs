// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::storage::SqliteStorage;
use rusqlite::types::{FromSql, FromSqlError, ToSql, ToSqlOutput, ValueRef};
use serde_derive::{Deserialize, Serialize};

#[derive(Deserialize)]
#[serde(tag = "kind", rename_all = "lowercase")]
pub(super) enum DBRequest {
    Query { sql: String, args: Vec<SqlValue> },
    Begin,
    Commit,
    Rollback,
}

#[derive(Serialize)]
#[serde(untagged)]
pub(super) enum DBResult {
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
            SqlValue::Blob(v) => ValueRef::Blob(&v),
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

pub(super) fn db_command_bytes(db: &SqliteStorage, input: &[u8]) -> Result<String> {
    let req: DBRequest = serde_json::from_slice(input)?;
    let resp = match req {
        DBRequest::Query { sql, args } => db_query(db, &sql, &args)?,
        DBRequest::Begin => {
            db.begin()?;
            DBResult::None
        }
        DBRequest::Commit => {
            db.commit()?;
            DBResult::None
        }
        DBRequest::Rollback => {
            db.rollback()?;
            DBResult::None
        }
    };
    Ok(serde_json::to_string(&resp)?)
}

pub(super) fn db_query(db: &SqliteStorage, sql: &str, args: &[SqlValue]) -> Result<DBResult> {
    let mut stmt = db.db.prepare_cached(sql)?;

    let columns = stmt.column_count();

    let res: std::result::Result<Vec<Vec<_>>, rusqlite::Error> = stmt
        .query_map(args, |row| {
            let mut orow = Vec::with_capacity(columns);
            for i in 0..columns {
                let v: SqlValue = row.get(i)?;
                orow.push(v);
            }
            Ok(orow)
        })?
        .collect();

    Ok(DBResult::Rows(res?))
}
