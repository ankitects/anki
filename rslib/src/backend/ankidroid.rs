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

    fn set_page_size(&self, input: generic::Int64) -> Result<()> {
        // we don't require an open collection, but should avoid modifying this
        // concurrently
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
