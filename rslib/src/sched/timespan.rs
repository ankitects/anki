// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::i18n::{tr_args, FString, I18n};

/// Short string like '4d' to place above answer buttons.
pub fn answer_button_time(seconds: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = match span.unit() {
        // months/years shown with 1 decimal place
        TimespanUnit::Months | TimespanUnit::Years => (span.as_unit() * 10.0).round() / 10.0,
        // other values shown without decimals
        _ => span.as_unit().round(),
    };
    let args = tr_args!["amount" => amount];
    let key = match span.unit() {
        TimespanUnit::Seconds => FString::SchedulingAnswerButtonTimeSeconds,
        TimespanUnit::Minutes => FString::SchedulingAnswerButtonTimeMinutes,
        TimespanUnit::Hours => FString::SchedulingAnswerButtonTimeHours,
        TimespanUnit::Days => FString::SchedulingAnswerButtonTimeDays,
        TimespanUnit::Months => FString::SchedulingAnswerButtonTimeMonths,
        TimespanUnit::Years => FString::SchedulingAnswerButtonTimeYears,
    };
    i18n.trn(key, args)
}

/// Describe the given seconds using the largest appropriate unit
/// eg 70 seconds -> "1.17 minutes"
pub fn time_span(seconds: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = span.as_unit();
    let args = tr_args!["amount" => amount];
    let key = match span.unit() {
        TimespanUnit::Seconds => FString::SchedulingTimeSpanSeconds,
        TimespanUnit::Minutes => FString::SchedulingTimeSpanMinutes,
        TimespanUnit::Hours => FString::SchedulingTimeSpanHours,
        TimespanUnit::Days => FString::SchedulingTimeSpanDays,
        TimespanUnit::Months => FString::SchedulingTimeSpanMonths,
        TimespanUnit::Years => FString::SchedulingTimeSpanYears,
    };
    i18n.trn(key, args)
}

// fixme: this doesn't belong here
pub fn studied_today(cards: usize, secs: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(secs).natural_span();
    let amount = span.as_unit();
    let unit = span.unit().as_str();
    let secs_per = if cards > 0 {
        secs / (cards as f32)
    } else {
        0.0
    };
    let args = tr_args!["amount" => amount, "unit" => unit,
        "cards" => cards, "secs-per-card" => secs_per];
    i18n.trn(FString::StatisticsStudiedToday, args)
}

// fixme: this doesn't belong here
pub fn learning_congrats(remaining: usize, next_due: f32, i18n: &I18n) -> String {
    // next learning card not due (/ until tomorrow)?
    if next_due == 0.0 || next_due >= 86_400.0 {
        return "".to_string();
    }

    let span = Timespan::from_secs(next_due).natural_span();
    let amount = span.as_unit().round();
    let unit = span.unit().as_str();
    let next_args = tr_args!["amount" => amount, "unit" => unit];
    let remaining_args = tr_args!["remaining" => remaining];
    format!(
        "{} {}",
        i18n.trn(FString::SchedulingNextLearnDue, next_args),
        i18n.trn(FString::SchedulingLearnRemaining, remaining_args)
    )
}

const SECOND: f32 = 1.0;
const MINUTE: f32 = 60.0 * SECOND;
const HOUR: f32 = 60.0 * MINUTE;
const DAY: f32 = 24.0 * HOUR;
const MONTH: f32 = 30.0 * DAY;
const YEAR: f32 = 365.0 * MONTH;

#[derive(Clone, Copy)]
enum TimespanUnit {
    Seconds,
    Minutes,
    Hours,
    Days,
    Months,
    Years,
}

impl TimespanUnit {
    fn as_str(self) -> &'static str {
        match self {
            TimespanUnit::Seconds => "seconds",
            TimespanUnit::Minutes => "minutes",
            TimespanUnit::Hours => "hours",
            TimespanUnit::Days => "days",
            TimespanUnit::Months => "months",
            TimespanUnit::Years => "years",
        }
    }
}

#[derive(Clone, Copy)]
struct Timespan {
    seconds: f32,
    unit: TimespanUnit,
}

impl Timespan {
    fn from_secs(seconds: f32) -> Self {
        Timespan {
            seconds,
            unit: TimespanUnit::Seconds,
        }
    }

    /// Return the value as the configured unit, eg seconds=70/unit=Minutes
    /// returns 1.17
    fn as_unit(self) -> f32 {
        let s = self.seconds;
        match self.unit {
            TimespanUnit::Seconds => s,
            TimespanUnit::Minutes => s / MINUTE,
            TimespanUnit::Hours => s / HOUR,
            TimespanUnit::Days => s / DAY,
            TimespanUnit::Months => s / MONTH,
            TimespanUnit::Years => s / YEAR,
        }
    }

    fn unit(self) -> TimespanUnit {
        self.unit
    }

    /// Return a new timespan in the most appropriate unit, eg
    /// 70 secs -> timespan in minutes
    fn natural_span(self) -> Timespan {
        let secs = self.seconds.abs();
        let unit = if secs < MINUTE {
            TimespanUnit::Seconds
        } else if secs < HOUR {
            TimespanUnit::Minutes
        } else if secs < DAY {
            TimespanUnit::Hours
        } else if secs < MONTH {
            TimespanUnit::Days
        } else if secs < YEAR {
            TimespanUnit::Months
        } else {
            TimespanUnit::Years
        };

        Timespan {
            seconds: self.seconds,
            unit,
        }
    }
}

#[cfg(test)]
mod test {
    use crate::i18n::I18n;
    use crate::sched::timespan::{
        answer_button_time, learning_congrats, studied_today, time_span, MONTH,
    };

    #[test]
    fn answer_buttons() {
        let i18n = I18n::new(&["zz"], "");
        assert_eq!(answer_button_time(30.0, &i18n), "30s");
        assert_eq!(answer_button_time(70.0, &i18n), "1m");
        assert_eq!(answer_button_time(1.1 * MONTH, &i18n), "1.1mo");
    }

    #[test]
    fn time_spans() {
        let i18n = I18n::new(&["zz"], "");
        assert_eq!(time_span(1.0, &i18n), "1 second");
        assert_eq!(time_span(30.0, &i18n), "30 seconds");
        assert_eq!(time_span(90.0, &i18n), "1.5 minutes");
    }

    #[test]
    fn combo() {
        // temporary test of fluent term handling
        let i18n = I18n::new(&["zz"], "");
        assert_eq!(
            &studied_today(3, 13.0, &i18n).replace("\n", " "),
            "Studied 3 cards in 13 seconds today (4.33s/card)"
        );
        assert_eq!(
            &learning_congrats(3, 3700.0, &i18n).replace("\n", " "),
            "The next learning card will be ready in 1 hour. There are 3 learning cards due later today."
        );
    }
}
