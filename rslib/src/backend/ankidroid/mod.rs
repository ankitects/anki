// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod db;
pub(crate) mod error;

pub(super) use anki_proto::ankidroid::ankidroid_service::Service as AnkidroidService;
use anki_proto::ankidroid::DbResponse;
use anki_proto::ankidroid::GetActiveSequenceNumbersResponse;
use anki_proto::ankidroid::GetNextResultPageRequest;
use anki_proto::generic;

use self::db::active_sequences;
use self::error::debug_produce_error;
use super::dbproxy::db_command_bytes;
use super::dbproxy::db_command_proto;
use super::Backend;
use crate::backend::ankidroid::db::execute_for_row_count;
use crate::backend::ankidroid::db::insert_for_id;
use crate::prelude::*;
use crate::scheduler::timing;
use crate::scheduler::timing::fixed_offset_from_minutes;

impl AnkidroidService for Backend {
    type Error = AnkiError;

    fn sched_timing_today_legacy(
        &self,
        input: anki_proto::ankidroid::SchedTimingTodayLegacyRequest,
    ) -> Result<anki_proto::scheduler::SchedTimingTodayResponse> {
        let result = timing::sched_timing_today(
            TimestampSecs::from(input.created_secs),
            TimestampSecs::from(input.now_secs),
            input.created_mins_west.map(fixed_offset_from_minutes),
            fixed_offset_from_minutes(input.now_mins_west),
            Some(input.rollover_hour as u8),
        )?;
        Ok(anki_proto::scheduler::SchedTimingTodayResponse::from(
            result,
        ))
    }

    fn local_minutes_west_legacy(&self, input: generic::Int64) -> Result<generic::Int32> {
        Ok(generic::Int32 {
            val: timing::local_minutes_west_for_stamp(input.val.into())?,
        })
    }

    fn run_db_command(&self, input: generic::Json) -> Result<generic::Json> {
        self.with_col(|col| db_command_bytes(col, &input.json))
            .map(|json| generic::Json { json })
    }

    fn run_db_command_proto(&self, input: generic::Json) -> Result<DbResponse> {
        self.with_col(|col| db_command_proto(col, &input.json))
    }

    fn run_db_command_for_row_count(&self, input: generic::Json) -> Result<generic::Int64> {
        self.with_col(|col| execute_for_row_count(col, &input.json))
            .map(|val| generic::Int64 { val })
    }

    fn flush_all_queries(&self) -> Result<()> {
        self.with_col(|col| {
            db::flush_collection(col);
            Ok(())
        })
    }

    fn flush_query(&self, input: generic::Int32) -> Result<()> {
        self.with_col(|col| {
            db::flush_single_result(col, input.val);
            Ok(())
        })
    }

    fn get_next_result_page(&self, input: GetNextResultPageRequest) -> Result<DbResponse> {
        self.with_col(|col| {
            db::get_next(col, input.sequence, input.index).or_invalid("missing result page")
        })
    }

    fn insert_for_id(&self, input: generic::Json) -> Result<generic::Int64> {
        self.with_col(|col| insert_for_id(col, &input.json).map(Into::into))
    }

    fn set_page_size(&self, input: generic::Int64) -> Result<()> {
        // we don't require an open collection, but should avoid modifying this
        // concurrently
        let _guard = self.col.lock();
        db::set_max_page_size(input.val as usize);
        Ok(())
    }

    fn get_column_names_from_query(&self, input: generic::String) -> Result<generic::StringList> {
        self.with_col(|col| {
            let stmt = col.storage.db.prepare(&input.val)?;
            let names = stmt.column_names();
            let names: Vec<_> = names.iter().map(ToString::to_string).collect();
            Ok(names.into())
        })
    }

    fn get_active_sequence_numbers(&self) -> Result<GetActiveSequenceNumbersResponse> {
        self.with_col(|col| {
            Ok(GetActiveSequenceNumbersResponse {
                numbers: active_sequences(col),
            })
        })
    }

    fn debug_produce_error(&self, input: generic::String) -> Result<()> {
        Err(debug_produce_error(&input.val))
    }
}

impl From<crate::scheduler::timing::SchedTimingToday>
    for anki_proto::scheduler::SchedTimingTodayResponse
{
    fn from(
        t: crate::scheduler::timing::SchedTimingToday,
    ) -> anki_proto::scheduler::SchedTimingTodayResponse {
        anki_proto::scheduler::SchedTimingTodayResponse {
            days_elapsed: t.days_elapsed,
            next_day_at: t.next_day_at.0,
        }
    }
}
