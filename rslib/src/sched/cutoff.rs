// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::timestamp::TimestampSecs;
use chrono::{Date, Duration, FixedOffset, Local, TimeZone, Timelike};

#[derive(Debug, PartialEq, Clone, Copy)]
pub struct SchedTimingToday {
    /// The number of days that have passed since the collection was created.
    pub days_elapsed: u32,
    /// Timestamp of the next day rollover.
    pub next_day_at: i64,
}

/// Timing information for the current day.
/// - created_secs is a UNIX timestamp of the collection creation time
/// - created_mins_west is the offset west of UTC at the time of creation
///   (eg UTC+10 hours is -600)
/// - now_secs is a timestamp of the current time
/// - now_mins_west is the current offset west of UTC
/// - rollover_hour is the hour of the day the rollover happens (eg 4 for 4am)
pub fn sched_timing_today_v2_new(
    created_secs: i64,
    created_mins_west: i32,
    now_secs: i64,
    now_mins_west: i32,
    rollover_hour: u8,
) -> SchedTimingToday {
    // get date(times) based on timezone offsets
    let created_date = fixed_offset_from_minutes(created_mins_west)
        .timestamp(created_secs, 0)
        .date();
    let now_datetime = fixed_offset_from_minutes(now_mins_west).timestamp(now_secs, 0);
    let today = now_datetime.date();

    // rollover
    let rollover_today_datetime = today.and_hms(rollover_hour as u32, 0, 0);
    let rollover_passed = rollover_today_datetime <= now_datetime;
    let next_day_at = if rollover_passed {
        (rollover_today_datetime + Duration::days(1)).timestamp()
    } else {
        rollover_today_datetime.timestamp()
    };

    // day count
    let days_elapsed = days_elapsed(created_date, today, rollover_passed);

    SchedTimingToday {
        days_elapsed,
        next_day_at,
    }
}

/// The number of times the day rolled over between two dates.
fn days_elapsed(
    start_date: Date<FixedOffset>,
    end_date: Date<FixedOffset>,
    rollover_passed: bool,
) -> u32 {
    let days = (end_date - start_date).num_days();

    // current day doesn't count before rollover time
    let days = if rollover_passed { days } else { days - 1 };

    // minimum of 0
    days.max(0) as u32
}

/// Build a FixedOffset struct, capping minutes_west if out of bounds.
fn fixed_offset_from_minutes(minutes_west: i32) -> FixedOffset {
    let bounded_minutes = minutes_west.max(-23 * 60).min(23 * 60);
    FixedOffset::west(bounded_minutes * 60)
}

/// For the given timestamp, return minutes west of UTC in the
/// local timezone.
/// eg, Australia at +10 hours is -600.
/// Includes the daylight savings offset if applicable.
pub fn local_minutes_west_for_stamp(stamp: i64) -> i32 {
    Local.timestamp(stamp, 0).offset().utc_minus_local() / 60
}

// Legacy code
// ----------------------------------

pub(crate) fn v1_rollover_from_creation_stamp(crt: i64) -> u8 {
    Local.timestamp(crt, 0).hour() as u8
}

pub(crate) fn v1_creation_date() -> i64 {
    let now = TimestampSecs::now();
    v1_creation_date_inner(now, local_minutes_west_for_stamp(now.0))
}

fn v1_creation_date_inner(now: TimestampSecs, mins_west: i32) -> i64 {
    let offset = fixed_offset_from_minutes(mins_west);
    let now_dt = offset.timestamp(now.0, 0);
    let four_am_dt = now_dt.date().and_hms(4, 0, 0);
    let four_am_stamp = four_am_dt.timestamp();

    if four_am_dt > now_dt {
        four_am_stamp - 86_400
    } else {
        four_am_stamp
    }
}

pub(crate) fn v1_creation_date_adjusted_to_hour(crt: i64, hour: u8) -> i64 {
    let offset = fixed_offset_from_minutes(local_minutes_west_for_stamp(crt));
    v1_creation_date_adjusted_to_hour_inner(crt, hour, offset)
}

fn v1_creation_date_adjusted_to_hour_inner(crt: i64, hour: u8, offset: FixedOffset) -> i64 {
    offset
        .timestamp(crt, 0)
        .date()
        .and_hms(hour as u32, 0, 0)
        .timestamp()
}

fn sched_timing_today_v1(crt: i64, now: i64) -> SchedTimingToday {
    let days_elapsed = (now - crt) / 86_400;
    let next_day_at = crt + (days_elapsed + 1) * 86_400;
    SchedTimingToday {
        days_elapsed: days_elapsed as u32,
        next_day_at,
    }
}

fn sched_timing_today_v2_legacy(
    crt: i64,
    rollover: u8,
    now: i64,
    mins_west: i32,
) -> SchedTimingToday {
    let offset = fixed_offset_from_minutes(mins_west);

    let crt_at_rollover = offset
        .timestamp(crt, 0)
        .date()
        .and_hms(rollover as u32, 0, 0)
        .timestamp();
    let days_elapsed = (now - crt_at_rollover) / 86_400;

    let mut next_day_at = offset
        .timestamp(now, 0)
        .date()
        .and_hms(rollover as u32, 0, 0)
        .timestamp();
    if next_day_at < now {
        next_day_at += 86_400;
    }

    SchedTimingToday {
        days_elapsed: days_elapsed as u32,
        next_day_at,
    }
}

// ----------------------------------

/// Based on provided input, get timing info from the relevant function.
pub(crate) fn sched_timing_today(
    created_secs: TimestampSecs,
    now_secs: TimestampSecs,
    created_mins_west: Option<i32>,
    now_mins_west: Option<i32>,
    rollover_hour: Option<u8>,
) -> SchedTimingToday {
    let now_west = now_mins_west.unwrap_or_else(|| local_minutes_west_for_stamp(now_secs.0));
    match (rollover_hour, created_mins_west) {
        (None, _) => {
            // if rollover unset, v1 scheduler
            sched_timing_today_v1(created_secs.0, now_secs.0)
        }
        (Some(roll), None) => {
            // if creationOffset unset, v2 scheduler with legacy cutoff handling
            sched_timing_today_v2_legacy(created_secs.0, roll, now_secs.0, now_west)
        }
        (Some(roll), Some(crt_west)) => {
            // v2 scheduler, new cutoff handling
            sched_timing_today_v2_new(created_secs.0, crt_west, now_secs.0, now_west, roll)
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use chrono::{FixedOffset, Local, TimeZone, Utc};

    // static timezone for tests
    const AEST_MINS_WEST: i32 = -600;

    #[test]
    fn fixed_offset() {
        let offset = fixed_offset_from_minutes(AEST_MINS_WEST);
        assert_eq!(offset.utc_minus_local(), AEST_MINS_WEST * 60);
    }

    // helper
    fn elap(start: i64, end: i64, start_west: i32, end_west: i32, rollhour: u8) -> u32 {
        let today = sched_timing_today_v2_new(start, start_west, end, end_west, rollhour);
        today.days_elapsed
    }

    #[test]
    #[cfg(target_vendor = "apple")]
    /// On Linux, TZ needs to be set prior to the process being started to take effect,
    /// so we limit this test to Macs.
    fn local_minutes_west() {
        // -480 throughout the year
        std::env::set_var("TZ", "Australia/Perth");
        assert_eq!(local_minutes_west_for_stamp(Utc::now().timestamp()), -480);
    }

    #[test]
    fn days_elapsed() {
        let local_offset = local_minutes_west_for_stamp(Utc::now().timestamp());

        let created_dt = FixedOffset::west(local_offset * 60)
            .ymd(2019, 12, 1)
            .and_hms(2, 0, 0);
        let crt = created_dt.timestamp();

        // days can't be negative
        assert_eq!(elap(crt, crt, local_offset, local_offset, 4), 0);
        assert_eq!(elap(crt, crt - 86_400, local_offset, local_offset, 4), 0);

        // 2am the next day is still the same day
        assert_eq!(elap(crt, crt + 24 * 3600, local_offset, local_offset, 4), 0);

        // day rolls over at 4am
        assert_eq!(elap(crt, crt + 26 * 3600, local_offset, local_offset, 4), 1);

        // the longest extra delay is +23, or 19 hours past the 4 hour default
        assert_eq!(
            elap(crt, crt + (26 + 18) * 3600, local_offset, local_offset, 23),
            0
        );
        assert_eq!(
            elap(crt, crt + (26 + 19) * 3600, local_offset, local_offset, 23),
            1
        );

        let mdt = FixedOffset::west(6 * 60 * 60);
        let mdt_offset = mdt.utc_minus_local() / 60;
        let mst = FixedOffset::west(7 * 60 * 60);
        let mst_offset = mst.utc_minus_local() / 60;

        // a collection created @ midnight in MDT in the past
        let crt = mdt.ymd(2018, 8, 6).and_hms(0, 0, 0).timestamp();
        // with the current time being MST
        let now = mst.ymd(2019, 12, 26).and_hms(20, 0, 0).timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 507);
        // the previous implementation generated a diferent elapsed number of days with a change
        // to DST, but the number shouldn't change
        assert_eq!(elap(crt, now, mdt_offset, mdt_offset, 4), 507);

        // collection created at 3am on the 6th, so day 1 starts at 4am on the 7th, and day 3 on the 9th.
        let crt = mdt.ymd(2018, 8, 6).and_hms(3, 0, 0).timestamp();
        let now = mst.ymd(2018, 8, 9).and_hms(1, 59, 59).timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 2);
        let now = mst.ymd(2018, 8, 9).and_hms(3, 59, 59).timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 2);
        let now = mst.ymd(2018, 8, 9).and_hms(4, 0, 0).timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 3);

        // try a bunch of combinations of creation time, current time, and rollover hour
        let hours_of_interest = &[0, 1, 4, 12, 22, 23];
        for creation_hour in hours_of_interest {
            let crt_dt = mdt.ymd(2018, 8, 6).and_hms(*creation_hour, 0, 0);
            let crt_stamp = crt_dt.timestamp();
            let crt_offset = mdt_offset;

            for current_day in 0..=3 {
                for current_hour in hours_of_interest {
                    for rollover_hour in hours_of_interest {
                        let end_dt = mdt
                            .ymd(2018, 8, 6 + current_day)
                            .and_hms(*current_hour, 0, 0);
                        let end_stamp = end_dt.timestamp();
                        let end_offset = mdt_offset;
                        let elap_day = if *current_hour < *rollover_hour {
                            current_day.max(1) - 1
                        } else {
                            current_day
                        };

                        assert_eq!(
                            elap(
                                crt_stamp,
                                end_stamp,
                                crt_offset,
                                end_offset,
                                *rollover_hour as u8
                            ),
                            elap_day
                        );
                    }
                }
            }
        }
    }

    #[test]
    fn next_day_at() {
        let rollhour = 4;
        let crt = Local.ymd(2019, 1, 1).and_hms(2, 0, 0);

        // before the rollover, the next day should be later on the same day
        let now = Local.ymd(2019, 1, 3).and_hms(2, 0, 0);
        let next_day_at = Local.ymd(2019, 1, 3).and_hms(rollhour, 0, 0);
        let today = sched_timing_today_v2_new(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as u8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local.ymd(2019, 1, 3).and_hms(rollhour, 0, 0);
        let next_day_at = Local.ymd(2019, 1, 4).and_hms(rollhour, 0, 0);
        let today = sched_timing_today_v2_new(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as u8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local.ymd(2019, 1, 3).and_hms(rollhour + 3, 0, 0);
        let next_day_at = Local.ymd(2019, 1, 4).and_hms(rollhour, 0, 0);
        let today = sched_timing_today_v2_new(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as u8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());
    }

    #[test]
    fn legacy_timing() {
        let now = 1584491078;

        assert_eq!(
            sched_timing_today_v1(1575226800, now),
            SchedTimingToday {
                days_elapsed: 107,
                next_day_at: 1584558000
            }
        );

        assert_eq!(
            sched_timing_today_v2_legacy(1533564000, 0, now, AEST_MINS_WEST),
            SchedTimingToday {
                days_elapsed: 589,
                next_day_at: 1584540000
            }
        );

        assert_eq!(
            sched_timing_today_v2_legacy(1524038400, 4, now, AEST_MINS_WEST),
            SchedTimingToday {
                days_elapsed: 700,
                next_day_at: 1584554400
            }
        );
    }

    #[test]
    fn legacy_creation_stamp() {
        let offset = fixed_offset_from_minutes(AEST_MINS_WEST);

        let now = TimestampSecs(offset.ymd(2020, 05, 10).and_hms(9, 30, 30).timestamp());
        assert_eq!(
            v1_creation_date_inner(now, AEST_MINS_WEST),
            offset.ymd(2020, 05, 10).and_hms(4, 0, 0).timestamp()
        );

        let now = TimestampSecs(offset.ymd(2020, 05, 10).and_hms(1, 30, 30).timestamp());
        assert_eq!(
            v1_creation_date_inner(now, AEST_MINS_WEST),
            offset.ymd(2020, 05, 9).and_hms(4, 0, 0).timestamp()
        );

        let crt = v1_creation_date_inner(now, AEST_MINS_WEST);
        assert_eq!(crt, v1_creation_date_adjusted_to_hour_inner(crt, 4, offset));
        assert_eq!(
            crt + 3600,
            v1_creation_date_adjusted_to_hour_inner(crt, 5, offset)
        );
        assert_eq!(
            crt - 3600 * 4,
            v1_creation_date_adjusted_to_hour_inner(crt, 0, offset)
        );
    }
}
