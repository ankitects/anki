// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time;

use chrono::prelude::*;

use crate::define_newtype;
use crate::prelude::*;

define_newtype!(TimestampSecs, i64);
define_newtype!(TimestampMillis, i64);

impl TimestampSecs {
    pub fn now() -> Self {
        Self(elapsed().as_secs() as i64)
    }

    pub fn zero() -> Self {
        Self(0)
    }

    pub fn elapsed_secs_since(self, other: TimestampSecs) -> i64 {
        self.0 - other.0
    }

    pub fn elapsed_secs(self) -> u64 {
        (Self::now().0 - self.0).max(0) as u64
    }

    pub fn elapsed_days_since(self, other: TimestampSecs) -> u64 {
        (self.0 - other.0).max(0) as u64 / 86_400
    }

    pub fn as_millis(self) -> TimestampMillis {
        TimestampMillis(self.0 * 1000)
    }

    pub(crate) fn local_datetime(self) -> Result<DateTime<Local>> {
        Local
            .timestamp_opt(self.0, 0)
            .latest()
            .or_invalid("invalid timestamp")
    }

    /// YYYY-mm-dd
    pub(crate) fn date_string(self) -> String {
        self.local_datetime()
            .map(|dt| dt.format("%Y-%m-%d").to_string())
            .unwrap_or_else(|_err| "invalid date".to_string())
    }

    /// HH-MM
    pub(crate) fn time_string(self) -> String {
        self.local_datetime()
            .map(|dt| dt.format("%H:%M").to_string())
            .unwrap_or_else(|_err| "invalid date".to_string())
    }

    pub(crate) fn date_and_time_string(self) -> String {
        format!("{} @ {}", self.date_string(), self.time_string())
    }

    pub fn local_utc_offset(self) -> Result<FixedOffset> {
        Ok(*self.local_datetime()?.offset())
    }

    pub fn datetime(self, utc_offset: FixedOffset) -> Result<DateTime<FixedOffset>> {
        utc_offset
            .timestamp_opt(self.0, 0)
            .latest()
            .or_invalid("invalid timestamp")
    }

    pub fn adding_secs(self, secs: i64) -> Self {
        TimestampSecs(self.0 + secs)
    }
}

impl TimestampMillis {
    pub fn now() -> Self {
        Self(elapsed().as_millis() as i64)
    }

    pub fn zero() -> Self {
        Self(0)
    }

    pub fn as_secs(self) -> TimestampSecs {
        TimestampSecs(self.0 / 1000)
    }

    pub fn adding_secs(self, secs: i64) -> Self {
        Self(self.0 + secs * 1000)
    }

    pub fn elapsed_millis(self) -> u64 {
        (Self::now().0 - self.0).max(0) as u64
    }
}

fn elapsed() -> time::Duration {
    if *crate::PYTHON_UNIT_TESTS {
        // shift clock around rollover time to accommodate Python tests that make bad
        // assumptions. we should update the tests in the future and remove this
        // hack.
        let mut elap = time::SystemTime::now()
            .duration_since(time::SystemTime::UNIX_EPOCH)
            .unwrap();
        let now = Local::now();
        if now.hour() >= 2 && now.hour() < 4 {
            elap -= time::Duration::from_secs(60 * 60 * 2);
        }
        elap
    } else {
        time::SystemTime::now()
            .duration_since(time::SystemTime::UNIX_EPOCH)
            .unwrap()
    }
}
