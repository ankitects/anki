// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use chrono::{Date, Duration, FixedOffset, Local, TimeZone};

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
pub fn sched_timing_today(
    created_secs: i64,
    created_mins_west: i32,
    now_secs: i64,
    now_mins_west: i32,
    rollover_hour: i8,
) -> SchedTimingToday {
    // get date(times) based on timezone offsets
    let created_date = fixed_offset_from_minutes(created_mins_west)
        .timestamp(created_secs, 0)
        .date();
    let now_datetime = fixed_offset_from_minutes(now_mins_west).timestamp(now_secs, 0);
    let today = now_datetime.date();

    // rollover
    let rollover_hour = normalized_rollover_hour(rollover_hour);
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

/// Negative rollover hours are relative to the next day, eg -1 = 23.
/// Cap hour to 23.
fn normalized_rollover_hour(hour: i8) -> u8 {
    let capped_hour = hour.max(-23).min(23);
    if capped_hour < 0 {
        (24 + capped_hour) as u8
    } else {
        capped_hour as u8
    }
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

#[cfg(test)]
mod test {
    use crate::sched::cutoff::{
        fixed_offset_from_minutes, local_minutes_west_for_stamp, normalized_rollover_hour,
        sched_timing_today,
    };
    use chrono::{FixedOffset, Local, TimeZone, Utc};

    #[test]
    fn rollover() {
        assert_eq!(normalized_rollover_hour(4), 4);
        assert_eq!(normalized_rollover_hour(23), 23);
        assert_eq!(normalized_rollover_hour(24), 23);
        assert_eq!(normalized_rollover_hour(-1), 23);
        assert_eq!(normalized_rollover_hour(-2), 22);
        assert_eq!(normalized_rollover_hour(-23), 1);
        assert_eq!(normalized_rollover_hour(-24), 1);
    }

    #[test]
    fn fixed_offset() {
        let offset = fixed_offset_from_minutes(-600);
        assert_eq!(offset.utc_minus_local(), -600 * 60);
    }

    // helper
    fn elap(start: i64, end: i64, start_west: i32, end_west: i32, rollhour: i8) -> u32 {
        let today = sched_timing_today(start, start_west, end, end_west, rollhour);
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
                                *rollover_hour as i8
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
        let today = sched_timing_today(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as i8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local.ymd(2019, 1, 3).and_hms(rollhour, 0, 0);
        let next_day_at = Local.ymd(2019, 1, 4).and_hms(rollhour, 0, 0);
        let today = sched_timing_today(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as i8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());

        // after the rollover, the next day should be the next day
        let now = Local.ymd(2019, 1, 3).and_hms(rollhour + 3, 0, 0);
        let next_day_at = Local.ymd(2019, 1, 4).and_hms(rollhour, 0, 0);
        let today = sched_timing_today(
            crt.timestamp(),
            crt.offset().utc_minus_local() / 60,
            now.timestamp(),
            now.offset().utc_minus_local() / 60,
            rollhour as i8,
        );
        assert_eq!(today.next_day_at, next_day_at.timestamp());
    }
}
