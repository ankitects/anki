// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod db;
pub(crate) mod error;

use self::db::active_sequences;
use self::error::debug_produce_error;
use super::dbproxy::db_command_bytes;
use super::dbproxy::db_command_proto;
use super::Backend;
use crate::backend::ankidroid::db::execute_for_row_count;
use crate::backend::ankidroid::db::insert_for_id;
use crate::pb;
pub(super) use crate::pb::ankidroid::ankidroid_service::Service as AnkidroidService;
use crate::pb::ankidroid::DbResponse;
use crate::pb::ankidroid::GetActiveSequenceNumbersResponse;
use crate::pb::ankidroid::GetNextResultPageRequest;
use crate::pb::generic;
use crate::pb::generic::Empty;
use crate::pb::generic::Int32;
use crate::pb::generic::Json;
use crate::prelude::*;
use crate::scheduler::timing;
use crate::scheduler::timing::fixed_offset_from_minutes;

impl AnkidroidService for Backend {
    fn sched_timing_today_legacy(
        &self,
        input: pb::ankidroid::SchedTimingTodayLegacyRequest,
    ) -> Result<pb::scheduler::SchedTimingTodayResponse> {
        let result = timing::sched_timing_today(
            TimestampSecs::from(input.created_secs),
            TimestampSecs::from(input.now_secs),
            input.created_mins_west.map(fixed_offset_from_minutes),
            fixed_offset_from_minutes(input.now_mins_west),
            Some(input.rollover_hour as u8),
        )?;
        Ok(pb::scheduler::SchedTimingTodayResponse::from(result))
    }

    fn local_minutes_west_legacy(&self, input: pb::generic::Int64) -> Result<pb::generic::Int32> {
        Ok(pb::generic::Int32 {
            val: timing::local_minutes_west_for_stamp(input.val.into())?,
        })
    }

    fn run_db_command(&self, input: Json) -> Result<Json> {
        self.with_col(|col| db_command_bytes(col, &input.json))
            .map(|json| Json { json })
    }

    fn run_db_command_proto(&self, input: Json) -> Result<DbResponse> {
        self.with_col(|col| db_command_proto(col, &input.json))
    }

    fn run_db_command_for_row_count(&self, input: Json) -> Result<pb::generic::Int64> {
        self.with_col(|col| execute_for_row_count(col, &input.json))
            .map(|val| pb::generic::Int64 { val })
    }

    fn flush_all_queries(&self, _input: Empty) -> Result<Empty> {
        self.with_col(|col| {
            db::flush_collection(col);
            Ok(Empty {})
        })
    }

    fn flush_query(&self, input: Int32) -> Result<Empty> {
        self.with_col(|col| {
            db::flush_single_result(col, input.val);
            Ok(Empty {})
        })
    }

    fn get_next_result_page(&self, input: GetNextResultPageRequest) -> Result<DbResponse> {
        self.with_col(|col| {
            db::get_next(col, input.sequence, input.index).or_invalid("missing result page")
        })
    }

    fn insert_for_id(&self, input: Json) -> Result<pb::generic::Int64> {
        self.with_col(|col| insert_for_id(col, &input.json).map(Into::into))
    }

    fn set_page_size(&self, input: pb::generic::Int64) -> Result<Empty> {
        // we don't require an open collection, but should avoid modifying this
        // concurrently
        let _guard = self.col.lock();
        db::set_max_page_size(input.val as usize);
        Ok(().into())
    }

    fn get_column_names_from_query(
        &self,
        input: generic::String,
    ) -> Result<pb::generic::StringList> {
        self.with_col(|col| {
            let stmt = col.storage.db.prepare(&input.val)?;
            let names = stmt.column_names();
            let names: Vec<_> = names.iter().map(ToString::to_string).collect();
            Ok(names.into())
        })
    }

    fn get_active_sequence_numbers(
        &self,
        _input: Empty,
    ) -> Result<GetActiveSequenceNumbersResponse> {
        self.with_col(|col| {
            Ok(GetActiveSequenceNumbersResponse {
                numbers: active_sequences(col),
            })
        })
    }

    fn debug_produce_error(&self, input: generic::String) -> Result<Empty> {
        Err(debug_produce_error(&input.val))
    }
}
