// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time;

use chrono::prelude::*;

use crate::{define_newtype, prelude::*};

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

    pub fn as_millis(self) -> TimestampMillis {
        TimestampMillis(self.0 * 1000)
    }

    #[cfg(windows)]
    pub(crate) fn local_datetime(self) -> Result<DateTime<Local>> {
        std::panic::catch_unwind(|| Local.timestamp(self.0, 0))
            .map_err(|_err| AnkiError::invalid_input("invalid date"))
    }

    #[cfg(not(windows))]
    pub(crate) fn local_datetime(self) -> Result<DateTime<Local>> {
        Ok(Local.timestamp(self.0, 0))
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

    pub fn local_utc_offset(self) -> Result<FixedOffset> {
        Ok(*self.local_datetime()?.offset())
    }

    pub fn datetime(self, utc_offset: FixedOffset) -> DateTime<FixedOffset> {
        utc_offset.timestamp(self.0, 0)
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
}

fn elapsed() -> time::Duration {
    if *crate::PYTHON_UNIT_TESTS {
        // shift clock around rollover time to accomodate Python tests that make bad assumptions.
        // we should update the tests in the future and remove this hack.
        let mut elap = time::SystemTime::now()
            .duration_since(time::SystemTime::UNIX_EPOCH)
            .unwrap();
        let now = Utc::now();
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
