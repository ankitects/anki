// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pb;
use crate::err::Result;
use crate::storage::SqliteStorage;
use rusqlite::types::{FromSql, FromSqlError, ToSql, ToSqlOutput, ValueRef};
use serde_derive::{Deserialize, Serialize};

// json implementation

#[derive(Deserialize)]
pub(super) struct DBRequest {
    sql: String,
    args: Vec<SqlValue>,
}

// #[derive(Serialize)]
// pub(super) struct DBResult {
//     rows: Vec<Vec<SqlValue>>,
// }
type DBResult = Vec<Vec<SqlValue>>;

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

pub(super) fn db_query_json_str(db: &SqliteStorage, input: &[u8]) -> Result<String> {
    let req: DBRequest = serde_json::from_slice(input)?;
    let resp = db_query_json(db, req)?;
    Ok(serde_json::to_string(&resp)?)
}

pub(super) fn db_query_json(db: &SqliteStorage, input: DBRequest) -> Result<DBResult> {
    let mut stmt = db.db.prepare_cached(&input.sql)?;

    let columns = stmt.column_count();

    let mut rows = stmt.query(&input.args)?;

    let mut output_rows = vec![];

    while let Some(row) = rows.next()? {
        let mut orow = Vec::with_capacity(columns);
        for i in 0..columns {
            let v: SqlValue = row.get(i)?;
            orow.push(v);
        }

        output_rows.push(orow);
    }

    Ok(output_rows)
}

// protobuf implementation

impl ToSql for pb::SqlValue {
    fn to_sql(&self) -> std::result::Result<ToSqlOutput<'_>, rusqlite::Error> {
        use pb::sql_value::Value as SqlValue;
        let val = match self
            .value
            .as_ref()
            .unwrap_or_else(|| &SqlValue::Null(pb::Empty {}))
        {
            SqlValue::Null(_) => ValueRef::Null,
            SqlValue::String(v) => ValueRef::Text(v.as_bytes()),
            SqlValue::Int(v) => ValueRef::Integer(*v),
            SqlValue::Double(v) => ValueRef::Real(*v),
            SqlValue::Blob(v) => ValueRef::Blob(&v),
        };
        Ok(ToSqlOutput::Borrowed(val))
    }
}

impl FromSql for pb::SqlValue {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        use pb::sql_value::Value as SqlValue;
        let val = match value {
            ValueRef::Null => SqlValue::Null(pb::Empty {}),
            ValueRef::Integer(i) => SqlValue::Int(i),
            ValueRef::Real(v) => SqlValue::Double(v),
            ValueRef::Text(v) => SqlValue::String(String::from_utf8_lossy(v).to_string()),
            ValueRef::Blob(v) => SqlValue::Blob(v.to_vec()),
        };
        Ok(pb::SqlValue { value: Some(val) })
    }
}

pub(super) fn db_query_proto(db: &SqliteStorage, input: pb::DbQueryIn) -> Result<pb::DbQueryOut> {
    let mut stmt = db.db.prepare_cached(&input.sql)?;

    let columns = stmt.column_count();

    let mut rows = stmt.query(&input.args)?;

    let mut output_rows = vec![];

    while let Some(row) = rows.next()? {
        let mut orow = Vec::with_capacity(columns);
        for i in 0..columns {
            let v: pb::SqlValue = row.get(i)?;
            orow.push(v);
        }

        output_rows.push(pb::SqlRow { values: orow });
    }

    Ok(pb::DbQueryOut { rows: output_rows })
}
