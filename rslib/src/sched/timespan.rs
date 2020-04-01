// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::i18n::{tr_args, I18n, TR};

/// Short string like '4d' to place above answer buttons.
pub fn answer_button_time(seconds: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let args = tr_args!["amount" => span.as_rounded_unit()];
    let key = match span.unit() {
        TimespanUnit::Seconds => TR::SchedulingAnswerButtonTimeSeconds,
        TimespanUnit::Minutes => TR::SchedulingAnswerButtonTimeMinutes,
        TimespanUnit::Hours => TR::SchedulingAnswerButtonTimeHours,
        TimespanUnit::Days => TR::SchedulingAnswerButtonTimeDays,
        TimespanUnit::Months => TR::SchedulingAnswerButtonTimeMonths,
        TimespanUnit::Years => TR::SchedulingAnswerButtonTimeYears,
    };
    i18n.trn(key, args)
}

/// Describe the given seconds using the largest appropriate unit.
/// If precise is true, show to two decimal places, eg
/// eg 70 seconds -> "1.17 minutes"
/// If false, seconds and days are shown without decimals.
pub fn time_span(seconds: f32, i18n: &I18n, precise: bool) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = if precise {
        span.as_unit()
    } else {
        span.as_rounded_unit()
    };
    let args = tr_args!["amount" => amount];
    let key = match span.unit() {
        TimespanUnit::Seconds => TR::SchedulingTimeSpanSeconds,
        TimespanUnit::Minutes => TR::SchedulingTimeSpanMinutes,
        TimespanUnit::Hours => TR::SchedulingTimeSpanHours,
        TimespanUnit::Days => TR::SchedulingTimeSpanDays,
        TimespanUnit::Months => TR::SchedulingTimeSpanMonths,
        TimespanUnit::Years => TR::SchedulingTimeSpanYears,
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
    i18n.trn(TR::StatisticsStudiedToday, args)
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
        i18n.trn(TR::SchedulingNextLearnDue, next_args),
        i18n.trn(TR::SchedulingLearnRemaining, remaining_args)
    )
}

const SECOND: f32 = 1.0;
const MINUTE: f32 = 60.0 * SECOND;
const HOUR: f32 = 60.0 * MINUTE;
const DAY: f32 = 24.0 * HOUR;
const MONTH: f32 = 30.0 * DAY;
const YEAR: f32 = 12.0 * MONTH;

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

    /// Round seconds and days to integers, otherwise
    /// truncates to one decimal place.
    fn as_rounded_unit(self) -> f32 {
        match self.unit {
            // seconds/days as integer
            TimespanUnit::Seconds | TimespanUnit::Days => self.as_unit().round(),
            // other values shown to 1 decimal place
            _ => (self.as_unit() * 10.0).round() / 10.0,
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
    use crate::log;
    use crate::sched::timespan::{
        answer_button_time, learning_congrats, studied_today, time_span, MONTH,
    };

    #[test]
    fn answer_buttons() {
        let log = log::terminal();
        let i18n = I18n::new(&["zz"], "", log);
        assert_eq!(answer_button_time(30.0, &i18n), "30s");
        assert_eq!(answer_button_time(70.0, &i18n), "1.2m");
        assert_eq!(answer_button_time(1.1 * MONTH, &i18n), "1.1mo");
    }

    #[test]
    fn time_spans() {
        let log = log::terminal();
        let i18n = I18n::new(&["zz"], "", log);
        assert_eq!(time_span(1.0, &i18n, false), "1 second");
        assert_eq!(time_span(30.3, &i18n, false), "30 seconds");
        assert_eq!(time_span(30.3, &i18n, true), "30.3 seconds");
        assert_eq!(time_span(90.0, &i18n, false), "1.5 minutes");
        assert_eq!(time_span(45.0 * 86_400.0, &i18n, false), "1.5 months");
        assert_eq!(time_span(365.0 * 86_400.0 * 1.5, &i18n, false), "1.5 years");
    }

    #[test]
    fn combo() {
        // temporary test of fluent term handling
        let log = log::terminal();
        let i18n = I18n::new(&["zz"], "", log);
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
