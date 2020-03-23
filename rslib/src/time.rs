// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time;

pub(crate) fn i64_unix_secs() -> i64 {
    elapsed().as_secs() as i64
}

pub(crate) fn i64_unix_millis() -> i64 {
    elapsed().as_millis() as i64
}

#[cfg(not(test))]
fn elapsed() -> time::Duration {
    time::SystemTime::now()
        .duration_since(time::SystemTime::UNIX_EPOCH)
        .unwrap()
}

// when running in CI, shift the current time away from the cutoff point
// to accomodate unit tests that depend on the current time
#[cfg(test)]
fn elapsed() -> time::Duration {
    use chrono::{Local, Timelike};

    let now = Local::now();

    let mut elap = time::SystemTime::now()
        .duration_since(time::SystemTime::UNIX_EPOCH)
        .unwrap();

    if now.hour() >= 2 && now.hour() < 4 {
        elap -= time::Duration::from_secs(60 * 60 * 2);
    }

    elap
}
