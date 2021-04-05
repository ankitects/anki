// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{collection::Collection, config::SchedulerVersion, error::Result, prelude::*};

pub mod answering;
pub mod bury_and_suspend;
pub(crate) mod congrats;
pub(crate) mod filtered;
mod learning;
pub mod new;
pub(crate) mod queue;
mod reviews;
pub mod states;
pub mod timespan;
pub mod timing;
mod upgrade;

use chrono::FixedOffset;
pub use reviews::parse_due_date_str;
use timing::{
    sched_timing_today, v1_creation_date_adjusted_to_hour, v1_rollover_from_creation_stamp,
    SchedTimingToday,
};

#[derive(Debug, Clone, Copy)]
pub struct SchedulerInfo {
    pub version: SchedulerVersion,
    pub timing: SchedTimingToday,
}

impl Collection {
    pub fn scheduler_info(&mut self) -> Result<SchedulerInfo> {
        let now = TimestampSecs::now();
        if let Some(info) = self.state.scheduler_info {
            if now < info.timing.next_day_at {
                return Ok(info);
            }
        }
        let version = self.scheduler_version();
        let timing = self.timing_for_timestamp(now)?;
        let info = SchedulerInfo { version, timing };
        self.state.scheduler_info = Some(info);
        Ok(info)
    }

    pub fn timing_today(&mut self) -> Result<SchedTimingToday> {
        self.scheduler_info().map(|info| info.timing)
    }

    pub fn current_due_day(&mut self, delta: i32) -> Result<u32> {
        Ok(((self.timing_today()?.days_elapsed as i32) + delta).max(0) as u32)
    }

    pub(crate) fn timing_for_timestamp(&mut self, now: TimestampSecs) -> Result<SchedTimingToday> {
        let current_utc_offset = self.local_utc_offset_for_user()?;

        let rollover_hour = match self.scheduler_version() {
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
    pub(crate) fn local_utc_offset_for_user(&mut self) -> Result<FixedOffset> {
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
        match self.scheduler_version() {
            SchedulerVersion::V1 => Ok(v1_rollover_from_creation_stamp(
                self.storage.creation_stamp()?.0,
            )),
            SchedulerVersion::V2 => Ok(self.get_v2_rollover().unwrap_or(4)),
        }
    }

    pub(crate) fn set_rollover_for_current_scheduler(&mut self, hour: u8) -> Result<()> {
        match self.scheduler_version() {
            SchedulerVersion::V1 => self.set_creation_stamp(TimestampSecs(
                v1_creation_date_adjusted_to_hour(self.storage.creation_stamp()?.0, hour),
            )),
            SchedulerVersion::V2 => self.set_v2_rollover(hour as u32),
        }
    }

    pub(crate) fn set_creation_stamp(&mut self, stamp: TimestampSecs) -> Result<()> {
        self.state.scheduler_info = None;
        self.storage.set_creation_stamp(stamp)
    }
}
