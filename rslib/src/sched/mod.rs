// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{collection::Collection, config::SchedulerVersion, err::Result, prelude::*};

pub mod bury_and_suspend;
pub(crate) mod congrats;
pub mod cutoff;
mod learning;
pub mod new;
mod reviews;
pub mod timespan;

use chrono::FixedOffset;
use cutoff::{
    sched_timing_today, v1_creation_date_adjusted_to_hour, v1_rollover_from_creation_stamp,
    SchedTimingToday,
};

impl Collection {
    pub fn timing_today(&self) -> Result<SchedTimingToday> {
        self.timing_for_timestamp(TimestampSecs::now())
    }

    pub fn current_due_day(&mut self, delta: i32) -> Result<u32> {
        Ok(((self.timing_today()?.days_elapsed as i32) + delta).max(0) as u32)
    }

    pub(crate) fn timing_for_timestamp(&self, now: TimestampSecs) -> Result<SchedTimingToday> {
        let current_utc_offset = self.local_utc_offset_for_user()?;

        let rollover_hour = match self.sched_ver() {
            SchedulerVersion::V1 => None,
            SchedulerVersion::V2 => {
                let configured_rollover = self.get_v2_rollover();
                match configured_rollover {
                    None => {
                        // an older Anki version failed to set this; correct
                        // the issue
                        self.set_v2_rollover(4)?;
                        Some(4)
                    }
                    val => val,
                }
            }
        };

        Ok(sched_timing_today(
            self.storage.creation_stamp()?,
            now,
            self.creation_utc_offset(),
            current_utc_offset,
            rollover_hour,
        ))
    }

    /// In the client case, return the current local timezone offset,
    /// ensuring the config reflects the current value.
    /// In the server case, return the value set in the config, and
    /// fall back on UTC if it's missing/invalid.
    pub(crate) fn local_utc_offset_for_user(&self) -> Result<FixedOffset> {
        let config_tz = self
            .get_configured_utc_offset()
            .and_then(|v| FixedOffset::west_opt(v * 60))
            .unwrap_or_else(|| FixedOffset::west(0));

        let local_tz = TimestampSecs::now().local_utc_offset();

        Ok(if self.server {
            config_tz
        } else {
            // if the timezone has changed, update the config
            if config_tz != local_tz {
                self.set_configured_utc_offset(local_tz.utc_minus_local() / 60)?;
            }
            local_tz
        })
    }

    /// Return the timezone offset at collection creation time. This should
    /// only be set when the V2 scheduler is active and the new timezone
    /// code is enabled.
    fn creation_utc_offset(&self) -> Option<FixedOffset> {
        self.get_creation_utc_offset()
            .and_then(|v| FixedOffset::west_opt(v * 60))
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
