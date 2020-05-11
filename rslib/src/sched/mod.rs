// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection, config::SchedulerVersion, err::Result, timestamp::TimestampSecs,
};

pub mod cutoff;
pub mod timespan;

use cutoff::{
    sched_timing_today, v1_creation_date_adjusted_to_hour, v1_rollover_from_creation_stamp,
    SchedTimingToday,
};

impl Collection {
    pub fn timing_today(&mut self) -> Result<SchedTimingToday> {
        self.timing_for_timestamp(TimestampSecs::now())
    }

    pub(crate) fn timing_for_timestamp(&mut self, now: TimestampSecs) -> Result<SchedTimingToday> {
        let local_offset = if self.server {
            self.get_local_mins_west()
        } else {
            None
        };

        Ok(sched_timing_today(
            self.storage.creation_stamp()?,
            now,
            self.get_creation_mins_west(),
            local_offset,
            self.get_v2_rollover(),
        ))
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

    pub(crate) fn learn_cutoff(&self) -> u32 {
        TimestampSecs::now().0 as u32 + self.learn_ahead_secs()
    }
}
