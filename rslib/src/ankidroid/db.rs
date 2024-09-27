// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::mem::size_of;
use std::sync::atomic::AtomicI32;
use std::sync::atomic::Ordering;
use std::sync::LazyLock;
use std::sync::Mutex;

use anki_proto::ankidroid::sql_value::Data;
use anki_proto::ankidroid::DbResponse;
use anki_proto::ankidroid::DbResult;
use anki_proto::ankidroid::Row;
use anki_proto::ankidroid::SqlValue;
use itertools::FoldWhile;
use itertools::FoldWhile::Continue;
use itertools::FoldWhile::Done;
use itertools::Itertools;
use rusqlite::ToSql;
use serde::Deserialize;

use crate::collection::Collection;
use crate::error::Result;

/// A pointer to the SqliteStorage object stored in a collection, used to
/// uniquely index results from multiple open collections at once.
impl Collection {
    fn id_for_db_cache(&self) -> CollectionId {
        CollectionId((&self.storage as *const _) as i64)
    }
}

#[derive(Hash, PartialEq, Eq)]
struct CollectionId(i64);

#[derive(Deserialize)]
struct DBArgs {
    sql: String,
    args: Vec<crate::backend::dbproxy::SqlValue>,
}

pub trait Sizable {
    /** Estimates the heap size of the value, in bytes */
    fn estimate_size(&self) -> usize;
}

impl Sizable for Data {
    fn estimate_size(&self) -> usize {
        match self {
            Data::StringValue(s) => s.len(),
            Data::LongValue(_) => size_of::<i64>(),
            Data::DoubleValue(_) => size_of::<f64>(),
            Data::BlobValue(b) => b.len(),
        }
    }
}

impl Sizable for SqlValue {
    fn estimate_size(&self) -> usize {
        // Add a byte for the optional
        self.data
            .as_ref()
            .map(|f| f.estimate_size() + 1)
            .unwrap_or(1)
    }
}

impl Sizable for Row {
    fn estimate_size(&self) -> usize {
        self.fields.iter().map(|x| x.estimate_size()).sum()
    }
}

impl Sizable for DbResult {
    fn estimate_size(&self) -> usize {
        // Performance: It might be best to take the first x rows and determine the data
        // types If we have floats or longs, they'll be a fixed size (excluding
        // nulls) and should speed up the calculation as we'll only calculate a
        // subset of the columns.
        self.rows.iter().map(|x| x.estimate_size()).sum()
    }
}

pub(crate) fn select_next_slice<'a>(rows: impl Iterator<Item = &'a Row>) -> Vec<Row> {
    select_slice_of_size(rows, get_max_page_size())
        .into_inner()
        .1
}

fn select_slice_of_size<'a>(
    mut rows: impl Iterator<Item = &'a Row>,
    max_size: usize,
) -> FoldWhile<(usize, Vec<Row>)> {
    let init: Vec<Row> = Vec::new();
    rows.fold_while((0, init), |mut acc, x| {
        let new_size = acc.0 + x.estimate_size();
        // If the accumulator is 0, but we're over the size: return a single result so
        // we don't loop forever. Theoretically, this shouldn't happen as data
        // should be reasonably sized
        if new_size > max_size && acc.0 > 0 {
            Done(acc)
        } else {
            // PERF: should be faster to return (size, numElements) then bulk copy/slice
            acc.1.push(x.to_owned());
            Continue((new_size, acc.1))
        }
    })
}

type SequenceNumber = i32;

static HASHMAP: LazyLock<Mutex<HashMap<CollectionId, HashMap<SequenceNumber, DbResponse>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

pub(crate) fn flush_single_result(col: &Collection, sequence_number: i32) {
    HASHMAP
        .lock()
        .unwrap()
        .get_mut(&col.id_for_db_cache())
        .map(|storage| storage.remove(&sequence_number));
}

pub(crate) fn flush_collection(col: &Collection) {
    HASHMAP.lock().unwrap().remove(&col.id_for_db_cache());
}

pub(crate) fn active_sequences(col: &Collection) -> Vec<i32> {
    HASHMAP
        .lock()
        .unwrap()
        .get(&col.id_for_db_cache())
        .map(|h| h.keys().copied().collect())
        .unwrap_or_default()
}

/**
Store the data in the cache if larger than than the page size.<br/>
Returns: The data capped to the page size
*/
pub(crate) fn trim_and_cache_remaining(
    col: &Collection,
    values: DbResult,
    sequence_number: i32,
) -> DbResponse {
    let start_index = 0;

    // PERF: Could speed this up by not creating the vector and just calculating the
    // count
    let first_result = select_next_slice(values.rows.iter());

    let row_count = values.rows.len() as i32;
    if first_result.len() < values.rows.len() {
        let to_store = DbResponse {
            result: Some(values),
            sequence_number,
            row_count,
            start_index,
        };
        insert_cache(col, to_store);

        DbResponse {
            result: Some(DbResult { rows: first_result }),
            sequence_number,
            row_count,
            start_index,
        }
    } else {
        DbResponse {
            result: Some(values),
            sequence_number,
            row_count,
            start_index,
        }
    }
}

fn insert_cache(col: &Collection, result: DbResponse) {
    HASHMAP
        .lock()
        .unwrap()
        .entry(col.id_for_db_cache())
        .or_default()
        .insert(result.sequence_number, result);
}

pub(crate) fn get_next(
    col: &Collection,
    sequence_number: i32,
    start_index: i64,
) -> Option<DbResponse> {
    let result = get_next_result(col, &sequence_number, start_index);

    if let Some(resp) = result.as_ref() {
        if resp.result.is_none() || resp.result.as_ref().unwrap().rows.is_empty() {
            flush_single_result(col, sequence_number)
        }
    }

    result
}

fn get_next_result(
    col: &Collection,
    sequence_number: &i32,
    start_index: i64,
) -> Option<DbResponse> {
    let map = HASHMAP.lock().unwrap();
    let result_map = map.get(&col.id_for_db_cache())?;
    let current_result = result_map.get(sequence_number)?;

    // TODO: This shouldn't need to exist
    let tmp: Vec<Row> = Vec::new();
    let next_rows = current_result
        .result
        .as_ref()
        .map(|x| x.rows.iter())
        .unwrap_or_else(|| tmp.iter());

    let skipped_rows = next_rows.clone().skip(start_index as usize).collect_vec();
    println!("{}", skipped_rows.len());

    let filtered_rows = select_next_slice(next_rows.skip(start_index as usize));

    let result = DbResult {
        rows: filtered_rows,
    };

    let trimmed_result = DbResponse {
        result: Some(result),
        sequence_number: current_result.sequence_number,
        row_count: current_result.row_count,
        start_index,
    };

    Some(trimmed_result)
}

static SEQUENCE_NUMBER: AtomicI32 = AtomicI32::new(0);

pub(crate) fn next_sequence_number() -> i32 {
    SEQUENCE_NUMBER.fetch_add(1, Ordering::SeqCst)
}

// same as we get from
// io.requery.android.database.CursorWindow.sCursorWindowSize
static DB_COMMAND_PAGE_SIZE: LazyLock<Mutex<usize>> = LazyLock::new(|| Mutex::new(1024 * 1024 * 2));

pub(crate) fn set_max_page_size(size: usize) {
    let mut state = DB_COMMAND_PAGE_SIZE.lock().expect("Could not lock mutex");
    *state = size;
}

fn get_max_page_size() -> usize {
    *DB_COMMAND_PAGE_SIZE.lock().unwrap()
}

fn get_args(in_bytes: &[u8]) -> Result<DBArgs> {
    let ret: DBArgs = serde_json::from_slice(in_bytes)?;
    Ok(ret)
}

pub(crate) fn insert_for_id(col: &Collection, json: &[u8]) -> Result<i64> {
    let req = get_args(json)?;
    let args: Vec<_> = req.args.iter().map(|a| a as &dyn ToSql).collect();
    col.storage.db.execute(&req.sql, &args[..])?;
    Ok(col.storage.db.last_insert_rowid())
}

pub(crate) fn execute_for_row_count(col: &Collection, req: &[u8]) -> Result<i64> {
    let req = get_args(req)?;
    let args: Vec<_> = req.args.iter().map(|a| a as &dyn ToSql).collect();
    let count = col.storage.db.execute(&req.sql, &args[..])?;
    Ok(count as i64)
}

#[cfg(test)]
mod tests {
    use anki_proto::ankidroid::sql_value;
    use anki_proto::ankidroid::Row;
    use anki_proto::ankidroid::SqlValue;

    use super::*;
    use crate::ankidroid::db::select_slice_of_size;
    use crate::ankidroid::db::Sizable;

    fn gen_data() -> Vec<SqlValue> {
        vec![
            SqlValue {
                data: Some(sql_value::Data::DoubleValue(12.0)),
            },
            SqlValue {
                data: Some(sql_value::Data::LongValue(12)),
            },
            SqlValue {
                data: Some(sql_value::Data::StringValue(
                    "Hellooooooo World".to_string(),
                )),
            },
            SqlValue {
                data: Some(sql_value::Data::BlobValue(vec![])),
            },
        ]
    }

    #[test]
    fn test_size_estimate() {
        let row = Row { fields: gen_data() };
        let result = DbResult {
            rows: vec![row.clone(), row],
        };

        let actual_size = result.estimate_size();

        let expected_size = (17 + 8 + 8) * 2; // 1 variable string, 1 long, 1 float
        let expected_overhead = 4 * 2; // 4 optional columns

        assert_eq!(actual_size, expected_overhead + expected_size);
    }

    #[test]
    fn test_stream_size() {
        let row = Row { fields: gen_data() };
        let result = DbResult {
            rows: vec![row.clone(), row.clone(), row],
        };
        let limit = 74 + 1; // two rows are 74

        let result = select_slice_of_size(result.rows.iter(), limit).into_inner();

        assert_eq!(
            2,
            result.1.len(),
            "The final element should not be included"
        );
        assert_eq!(
            74, result.0,
            "The size should be the size of the first two objects"
        );
    }

    #[test]
    fn test_stream_size_too_small() {
        let row = Row { fields: gen_data() };
        let result = DbResult { rows: vec![row] };
        let limit = 1;

        let result = select_slice_of_size(result.rows.iter(), limit).into_inner();

        assert_eq!(
            1,
            result.1.len(),
            "If the limit is too small, a result is still returned"
        );
        assert_eq!(
            37, result.0,
            "The size should be the size of the first objects"
        );
    }

    const SEQUENCE_NUMBER: i32 = 1;

    fn get(col: &Collection, index: i64) -> Option<DbResponse> {
        get_next(col, SEQUENCE_NUMBER, index)
    }

    fn get_first(col: &Collection, result: DbResult) -> DbResponse {
        trim_and_cache_remaining(col, result, SEQUENCE_NUMBER)
    }

    fn seq_number_used(col: &Collection) -> bool {
        HASHMAP
            .lock()
            .unwrap()
            .get(&col.id_for_db_cache())
            .unwrap()
            .contains_key(&SEQUENCE_NUMBER)
    }

    #[test]
    fn integration_test() {
        let col = Collection::new();

        let row = Row { fields: gen_data() };

        // return one row at a time
        set_max_page_size(row.estimate_size() - 1);

        let db_query_result = DbResult {
            rows: vec![row.clone(), row],
        };

        let first_jni_response = get_first(&col, db_query_result);

        assert_eq!(
            row_count(&first_jni_response),
            1,
            "The first call should only return one row"
        );

        let next_index = first_jni_response.start_index + row_count(&first_jni_response);

        let second_response = get(&col, next_index);

        assert!(
            second_response.is_some(),
            "The second response should return a value"
        );
        let valid_second_response = second_response.unwrap();
        assert_eq!(row_count(&valid_second_response), 1);

        let final_index = valid_second_response.start_index + row_count(&valid_second_response);

        assert!(seq_number_used(&col), "The sequence number is assigned");

        let final_response = get(&col, final_index);
        assert!(
            final_response.is_some(),
            "The third call should return something with no rows"
        );
        assert_eq!(
            row_count(&final_response.unwrap()),
            0,
            "The third call should return something with no rows"
        );
        assert!(
            !seq_number_used(&col),
            "Sequence number data has been cleared"
        );
    }

    fn row_count(resp: &DbResponse) -> i64 {
        resp.result.as_ref().map(|x| x.rows.len()).unwrap_or(0) as i64
    }
}
