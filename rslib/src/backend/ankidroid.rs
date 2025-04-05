// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use super::Backend;
use crate::ankidroid::db;
use crate::ankidroid::error::debug_produce_error;
use crate::prelude::*;
use crate::scheduler::timing;
use crate::scheduler::timing::fixed_offset_from_minutes;
use crate::services::BackendAnkidroidService;

impl BackendAnkidroidService for Backend {
    fn sched_timing_today_legacy(
        &self,
        input: anki_proto::ankidroid::SchedTimingTodayLegacyRequest,
    ) -> Result<anki_proto::scheduler::SchedTimingTodayResponse> {
        
        
        // Convert timestamps properly
        let created_secs = TimestampSecs(input.created_secs);
        let now_secs = TimestampSecs(input.now_secs);
        
        
        let created_offset = input.created_mins_west.map(fixed_offset_from_minutes);
        let now_offset = fixed_offset_from_minutes(input.now_mins_west);
        
        let result = timing::sched_timing_today(
            created_secs,
            now_secs,
            created_offset,
            now_offset,
            Some(input.rollover_hour as u8),
        )?;
        
        Ok(anki_proto::scheduler::SchedTimingTodayResponse::from(
            result,
        ))
    }

    fn local_minutes_west_legacy(&self, input: generic::Int64) -> Result<generic::Int32> {
        let timestamp = TimestampSecs(input.val);
        Ok(generic::Int32 {
            val: timing::local_minutes_west_for_stamp(timestamp)?,
        })
    }

    fn set_page_size(&self, input: generic::Int64) -> Result<()> {
        
        let _guard = self.col.lock();
        db::set_max_page_size(input.val as usize);
        Ok(())
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