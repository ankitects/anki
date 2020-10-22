// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection, config::SchedulerVersion, err::Result, timestamp::TimestampSecs,
};

pub mod bury_and_suspend;
pub(crate) mod congrats;
pub mod cutoff;
mod learning;
pub mod new;
mod reviews;
pub mod timespan;

use chrono::FixedOffset;
use cutoff::{
    fixed_offset_from_minutes, local_minutes_west_for_stamp, sched_timing_today,
    v1_creation_date_adjusted_to_hour, v1_rollover_from_creation_stamp, SchedTimingToday,
};

impl Collection {
    pub fn timing_today(&self) -> Result<SchedTimingToday> {
        self.timing_for_timestamp(TimestampSecs::now())
    }

    pub fn current_due_day(&mut self, delta: i32) -> Result<u32> {
        Ok(((self.timing_today()?.days_elapsed as i32) + delta).max(0) as u32)
    }

    pub(crate) fn timing_for_timestamp(&self, now: TimestampSecs) -> Result<SchedTimingToday> {
        let local_offset = if self.server {
            self.get_local_mins_west()
        } else {
            None
        };

        let rollover_hour = match self.sched_ver() {
            SchedulerVersion::V1 => None,
            SchedulerVersion::V2 => self.get_v2_rollover().or(Some(4)),
        };

        Ok(sched_timing_today(
            self.storage.creation_stamp()?,
            now,
            self.get_creation_mins_west(),
            local_offset,
            rollover_hour,
        ))
    }

    /// Get the local timezone.
    /// We could use this to simplify timing_for_timestamp() in the future
    pub(crate) fn local_offset(&self) -> FixedOffset {
        let local_mins_west = if self.server {
            self.get_local_mins_west()
        } else {
            None
        };
        let local_mins_west =
            local_mins_west.unwrap_or_else(|| local_minutes_west_for_stamp(TimestampSecs::now().0));
        fixed_offset_from_minutes(local_mins_west)
    }

    pub fn rollover_for_current_scheduler(&self) -> Result<u8> {
        match self.sched_ver() {
            SchedulerVersion::V1 => Ok(v1_rollover_from_creation_stamp(
                self.storage.creation_stamp()?.0,
            )),
            SchedulerVersion::V2 => Ok(self.get_v2_rollover().unwrap_or(4)),
        }
    }

    pub(crate) fn set_rollover_for_current_scheduler(&self, hour: u8) -> Result<()> {
        match self.sched_ver() {
            SchedulerVersion::V1 => {
                self.storage
                    .set_creation_stamp(TimestampSecs(v1_creation_date_adjusted_to_hour(
                        self.storage.creation_stamp()?.0,
                        hour,
                    )))
            }
            SchedulerVersion::V2 => self.set_v2_rollover(hour as u32),
        }
    }
}
