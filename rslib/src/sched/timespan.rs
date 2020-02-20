// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::i18n::{tr_args, I18n, StringsGroup};

pub fn answer_button_time(seconds: f32, i18n: &I18n) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = match span.unit() {
        TimespanUnit::Months | TimespanUnit::Years => span.as_unit(),
        // we don't show fractional value except for months/years
        _ => span.as_unit().round(),
    };
    let unit = span.unit().as_str();
    let args = tr_args!["amount" => amount];
    i18n.get(StringsGroup::Scheduling)
        .trn(&format!("answer-button-time-{}", unit), args)
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
    use crate::sched::timespan::{answer_button_time, MONTH};

    #[test]
    fn answer_buttons() {
        let i18n = I18n::new(&["zz"], "");
        assert_eq!(answer_button_time(30.0, &i18n), "30s");
        assert_eq!(answer_button_time(70.0, &i18n), "1m");
        assert_eq!(answer_button_time(1.1 * MONTH, &i18n), "1.10mo");
    }
}
