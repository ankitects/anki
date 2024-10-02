// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use chrono::DateTime;
use chrono::Datelike;
use chrono::Duration;
use chrono::FixedOffset;
use chrono::Timelike;

use crate::prelude::*;

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub struct SchedTimingToday {
    pub now: TimestampSecs,
    /// The number of days that have passed since the collection was created.
    pub days_elapsed: u32,
    /// Timestamp of the next day rollover.
    pub next_day_at: TimestampSecs,
}

/// Timing information for the current day.
/// - creation_secs is a UNIX timestamp of the collection creation time
/// - creation_utc_offset is the UTC offset at collection creation time
/// - current_secs is a timestamp of the current time
/// - current_utc_offset is the current UTC offset
/// - rollover_hour is the hour of the day the rollover happens (eg 4 for 4am)
pub fn sched_timing_today_v2_new(
    creation_secs: TimestampSecs,
    creation_utc_offset: FixedOffset,
    current_secs: TimestampSecs,
    current_utc_offset: FixedOffset,
    rollover_hour: u8,
) -> Result<SchedTimingToday> {
    // get date(times) based on timezone offsets
    let created_datetime = creation_secs.datetime(creation_utc_offset)?;
    let now_datetime = current_secs.datetime(current_utc_offset)?;

    // rollover
    let rollover_today_datetime = rollover_datetime(now_datetime, rollover_hour);
    let rollover_passed = rollover_today_datetime <= now_datetime;
    let next_day_at = TimestampSecs(if rollover_passed {
        (rollover_today_datetime + Duration::days(1)).timestamp()
    } else {
        rollover_today_datetime.timestamp()
    });

    // day count
    let days_elapsed = days_elapsed(created_datetime, now_datetime, rollover_passed);

    Ok(SchedTimingToday {
        now: current_secs,
        days_elapsed,
        next_day_at,
    })
}

fn rollover_datetime(date: DateTime<FixedOffset>, rollover_hour: u8) -> DateTime<FixedOffset> {
    date.with_hour((rollover_hour % 24) as u32)
        .unwrap()
        .with_minute(0)
        .unwrap()
        .with_second(0)
        .unwrap()
        .with_nanosecond(0)
        .unwrap()
}

/// The number of times the day rolled over between two dates.
fn days_elapsed(
    start_date: DateTime<FixedOffset>,
    end_date: DateTime<FixedOffset>,
    rollover_passed: bool,
) -> u32 {
    let days = end_date.num_days_from_ce() - start_date.num_days_from_ce();

    // current day doesn't count before rollover time
    let days = if rollover_passed { days } else { days - 1 };

    // minimum of 0
    days.max(0) as u32
}

/// Build a FixedOffset struct, capping minutes_west if out of bounds.
pub(crate) fn fixed_offset_from_minutes(minutes_west: i32) -> FixedOffset {
    let bounded_minutes = minutes_west.clamp(-23 * 60, 23 * 60);
    FixedOffset::west_opt(bounded_minutes * 60).unwrap()
}

/// For the given timestamp, return minutes west of UTC in the
/// local timezone.
/// eg, Australia at +10 hours is -600.
/// Includes the daylight savings offset if applicable.
pub fn local_minutes_west_for_stamp(stamp: TimestampSecs) -> Result<i32> {
    Ok(stamp.local_datetime()?.offset().utc_minus_local() / 60)
}

pub(crate) fn v1_creation_date() -> i64 {
    let now = TimestampSecs::now();
    v1_creation_date_inner(now, local_minutes_west_for_stamp(now).unwrap())
}

fn v1_creation_date_inner(now: TimestampSecs, mins_west: i32) -> i64 {
    let offset = fixed_offset_from_minutes(mins_west);
    let now_dt = now.datetime(offset).unwrap();
    let four_am_dt = rollover_datetime(now_dt, 4);
    let four_am_stamp = four_am_dt.timestamp();

    if four_am_dt > now_dt {
        four_am_stamp - 86_400
    } else {
        four_am_stamp
    }
}

fn sched_timing_today_v1(crt: TimestampSecs, now: TimestampSecs) -> SchedTimingToday {
    let days_elapsed = (now.0 - crt.0) / 86_400;
    let next_day_at = TimestampSecs(crt.0 + (days_elapsed + 1) * 86_400);
    SchedTimingToday {
        now,
        days_elapsed: days_elapsed as u32,
        next_day_at,
    }
}

fn sched_timing_today_v2_legacy(
    crt: TimestampSecs,
    rollover: u8,
    now: TimestampSecs,
    current_utc_offset: FixedOffset,
) -> Result<SchedTimingToday> {
    let crt_at_rollover =
        rollover_datetime(crt.datetime(current_utc_offset)?, rollover).timestamp();
    let days_elapsed = (now.0 - crt_at_rollover) / 86_400;

    let mut next_day_at =
        TimestampSecs(rollover_datetime(now.datetime(current_utc_offset)?, rollover).timestamp());
    if next_day_at < now {
        next_day_at = next_day_at.adding_secs(86_400);
    }

    Ok(SchedTimingToday {
        now,
        days_elapsed: days_elapsed as u32,
        next_day_at,
    })
}

// ----------------------------------

/// Decide which scheduler timing to use based on the provided input,
/// and return the relevant timing info.
pub(crate) fn sched_timing_today(
    creation_secs: TimestampSecs,
    current_secs: TimestampSecs,
    creation_utc_offset: Option<FixedOffset>,
    current_utc_offset: FixedOffset,
    rollover_hour: Option<u8>,
) -> Result<SchedTimingToday> {
    match (rollover_hour, creation_utc_offset) {
        (None, _) => {
            // if rollover unset, v1 scheduler
            Ok(sched_timing_today_v1(creation_secs, current_secs))
        }
        (Some(rollover), None) => {
            // if creationOffset unset, v2 scheduler with legacy cutoff handling
            sched_timing_today_v2_legacy(creation_secs, rollover, current_secs, current_utc_offset)
        }
        (Some(rollover), Some(creation_utc_offset)) => {
            // v2 scheduler, new cutoff handling
            sched_timing_today_v2_new(
                creation_secs,
                creation_utc_offset,
                current_secs,
                current_utc_offset,
                rollover,
            )
        }
    }
}

/// True if provided due number looks like a seconds-based timestamp.
pub fn is_unix_epoch_timestamp(due: i32) -> bool {
    due > 1_000_000_000
}

#[cfg(test)]
mod test {
    use chrono::FixedOffset;
    use chrono::Local;
    use chrono::TimeZone;

    use super::*;

    // test helper
    impl SchedTimingToday {
        /// Check if less than 25 minutes until the rollover
        pub fn near_cutoff(&self) -> bool {
            let near = TimestampSecs::now().adding_secs(60 * 25) > self.next_day_at;
            if near {
                println!("this would fail near the rollover time");
            }
            near
        }
    }

    // static timezone for tests
    const AEST_MINS_WEST: i32 = -600;

    fn aest_offset() -> FixedOffset {
        FixedOffset::west_opt(AEST_MINS_WEST * 60).unwrap()
    }

    #[test]
    fn fixed_offset() {
        let offset = fixed_offset_from_minutes(AEST_MINS_WEST);
        assert_eq!(offset.utc_minus_local(), AEST_MINS_WEST * 60);
    }

    // helper
    fn elap(start: i64, end: i64, start_west: i32, end_west: i32, rollhour: u8) -> u32 {
        let start = TimestampSecs(start);
        let end = TimestampSecs(end);
        let start_west = FixedOffset::west_opt(start_west * 60).unwrap();
        let end_west = FixedOffset::west_opt(end_west * 60).unwrap();
        let today = sched_timing_today_v2_new(start, start_west, end, end_west, rollhour).unwrap();
        today.days_elapsed
    }

    #[test]
    fn days_elapsed() {
        let local_offset = local_minutes_west_for_stamp(TimestampSecs::now()).unwrap();

        let created_dt = FixedOffset::west_opt(local_offset * 60)
            .unwrap()
            .with_ymd_and_hms(2019, 12, 1, 2, 0, 0)
            .latest()
            .unwrap();
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

        let mdt = FixedOffset::west_opt(6 * 60 * 60).unwrap();
        let mdt_offset = mdt.utc_minus_local() / 60;
        let mst = FixedOffset::west_opt(7 * 60 * 60).unwrap();
        let mst_offset = mst.utc_minus_local() / 60;

        // a collection created @ midnight in MDT in the past
        let crt = mdt
            .with_ymd_and_hms(2018, 8, 6, 0, 0, 0)
            .latest()
            .unwrap()
            .timestamp();
        // with the current time being MST
        let now = mst
            .with_ymd_and_hms(2019, 12, 26, 20, 0, 0)
            .latest()
            .unwrap()
            .timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 507);
        // the previous implementation generated a different elapsed number of days with
        // a change to DST, but the number shouldn't change
        assert_eq!(elap(crt, now, mdt_offset, mdt_offset, 4), 507);

        // collection created at 3am on the 6th, so day 1 starts at 4am on the 7th, and
        // day 3 on the 9th.
        let crt = mdt
            .with_ymd_and_hms(2018, 8, 6, 3, 0, 0)
            .latest()
            .unwrap()
            .timestamp();
        let now = mst
            .with_ymd_and_hms(2018, 8, 9, 1, 59, 59)
            .latest()
            .unwrap()
            .timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 2);
        let now = mst
            .with_ymd_and_hms(2018, 8, 9, 3, 59, 59)
            .latest()
            .unwrap()
            .timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 2);
        let now = mst
            .with_ymd_and_hms(2018, 8, 9, 4, 0, 0)
            .latest()
            .unwrap()
            .timestamp();
        assert_eq!(elap(crt, now, mdt_offset, mst_offset, 4), 3);

        // try a bunch of combinations of creation time, current time, and rollover hour
        let hours_of_interest = &[0, 1, 4, 12, 22, 23];
        for creation_hour in hours_of_interest {
            let crt_dt = mdt
                .with_ymd_and_hms(2018, 8, 6, *creation_hour, 0, 0)
                .latest()
                .unwrap();
            let crt_stamp = crt_dt.timestamp();
            let crt_offset = mdt_offset;

            for current_day in 0..=3 {
                for current_hour in hours_of_interest {
                    for rollover_hour in hours_of_interest {
                        let end_dt = mdt
                            .with_ymd_and_hms(2018, 8, 6 + current_day, *current_hour, 0, 0)
                            .latest()
                            .unwrap();
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
        let crt = Local
            .with_ymd_and_hms(2019, 1, 1, 2, 0, 0)
            .latest()
            .unwrap();

        // before the rollover, the next day should be later on the same day
        let now = Local
            .with_ymd_and_hms(2019, 1, 3, 2, 0, 0)
            .latest()
            .unwrap();
        let next_day_at = Local
            .with_ymd_and_hms(2019, 1, 3, rollhour, 0, 0)
            .latest()
            .unwrap();
        let today = sched_timing_today_v2_new(
            TimestampSecs(crt.timestamp()),
            *crt.offset(),
            TimestampSecs(now.timestamp()),
            *now.offset(),
            rollhour as u8,
        )
        .unwrap();
        assert_eq!(today.next_day_at.0, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local
            .with_ymd_and_hms(2019, 1, 3, rollhour, 0, 0)
            .latest()
            .unwrap();
        let next_day_at = Local
            .with_ymd_and_hms(2019, 1, 4, rollhour, 0, 0)
            .latest()
            .unwrap();
        let today = sched_timing_today_v2_new(
            TimestampSecs(crt.timestamp()),
            *crt.offset(),
            TimestampSecs(now.timestamp()),
            *now.offset(),
            rollhour as u8,
        )
        .unwrap();
        assert_eq!(today.next_day_at.0, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local
            .with_ymd_and_hms(2019, 1, 3, rollhour + 3, 0, 0)
            .latest()
            .unwrap();
        let next_day_at = Local
            .with_ymd_and_hms(2019, 1, 4, rollhour, 0, 0)
            .latest()
            .unwrap();
        let today = sched_timing_today_v2_new(
            TimestampSecs(crt.timestamp()),
            *crt.offset(),
            TimestampSecs(now.timestamp()),
            *now.offset(),
            rollhour as u8,
        )
        .unwrap();
        assert_eq!(today.next_day_at.0, next_day_at.timestamp());
    }

    #[test]
    fn legacy_timing() {
        let now = TimestampSecs(1584491078);

        assert_eq!(
            sched_timing_today_v1(TimestampSecs(1575226800), now),
            SchedTimingToday {
                now,
                days_elapsed: 107,
                next_day_at: TimestampSecs(1584558000)
            }
        );

        assert_eq!(
            sched_timing_today_v2_legacy(TimestampSecs(1533564000), 0, now, aest_offset()),
            Ok(SchedTimingToday {
                now,
                days_elapsed: 589,
                next_day_at: TimestampSecs(1584540000)
            })
        );

        assert_eq!(
            sched_timing_today_v2_legacy(TimestampSecs(1524038400), 4, now, aest_offset()),
            Ok(SchedTimingToday {
                now,
                days_elapsed: 700,
                next_day_at: TimestampSecs(1584554400)
            })
        );
    }

    #[test]
    fn legacy_creation_stamp() {
        let offset = fixed_offset_from_minutes(AEST_MINS_WEST);

        let now = TimestampSecs(
            offset
                .with_ymd_and_hms(2020, 5, 10, 9, 30, 30)
                .latest()
                .unwrap()
                .timestamp(),
        );
        assert_eq!(
            v1_creation_date_inner(now, AEST_MINS_WEST),
            offset
                .with_ymd_and_hms(2020, 5, 10, 4, 0, 0)
                .latest()
                .unwrap()
                .timestamp()
        );

        let now = TimestampSecs(
            offset
                .with_ymd_and_hms(2020, 5, 10, 1, 30, 30)
                .latest()
                .unwrap()
                .timestamp(),
        );
        assert_eq!(
            v1_creation_date_inner(now, AEST_MINS_WEST),
            offset
                .with_ymd_and_hms(2020, 5, 9, 4, 0, 0)
                .latest()
                .unwrap()
                .timestamp()
        );
    }
}
