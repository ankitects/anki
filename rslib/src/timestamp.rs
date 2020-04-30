// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::define_newtype;
use lazy_static::lazy_static;
use std::env;
use std::time;

define_newtype!(TimestampSecs, i64);
define_newtype!(TimestampMillis, i64);

impl TimestampSecs {
    pub fn now() -> Self {
        Self(elapsed().as_secs() as i64)
    }
}

impl TimestampMillis {
    pub fn now() -> Self {
        Self(elapsed().as_millis() as i64)
    }
}

lazy_static! {
    static ref TESTING: bool = env::var("SHIFT_CLOCK_HACK").is_ok();
}

fn elapsed() -> time::Duration {
    if *TESTING {
        // shift clock around rollover time to accomodate Python tests that make bad assumptions.
        // we should update the tests in the future and remove this hack.
        use chrono::{Local, Timelike};
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
