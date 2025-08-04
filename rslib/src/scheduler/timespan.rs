// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_i18n::I18n;

/// Short string like '4d' to place above answer buttons.
pub fn answer_button_time(seconds: f32, tr: &I18n) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = span.as_rounded_unit_for_answer_buttons();
    match span.unit() {
        TimespanUnit::Seconds => tr.scheduling_answer_button_time_seconds(amount),
        TimespanUnit::Minutes => tr.scheduling_answer_button_time_minutes(amount),
        TimespanUnit::Hours => tr.scheduling_answer_button_time_hours(amount),
        TimespanUnit::Days => tr.scheduling_answer_button_time_days(amount),
        TimespanUnit::Months => tr.scheduling_answer_button_time_months(amount),
        TimespanUnit::Years => tr.scheduling_answer_button_time_years(amount),
    }
    .into()
}

/// Short string like '4d' to place above answer buttons.
/// Times within the collapse time are represented like '<10m'
pub fn answer_button_time_collapsible(seconds: u32, collapse_secs: u32, tr: &I18n) -> String {
    let string = answer_button_time(seconds as f32, tr);
    if seconds == 0 {
        tr.scheduling_end().into()
    } else if seconds < collapse_secs {
        format!("<{string}")
    } else {
        string
    }
}

/// Describe the given seconds using the largest appropriate unit.
/// If precise is true, show to two decimal places, eg
/// eg 70 seconds -> "1.17 minutes"
/// If false, seconds and days are shown without decimals.
pub fn time_span(seconds: f32, tr: &I18n, precise: bool) -> String {
    let span = Timespan::from_secs(seconds).natural_span();
    let amount = if precise {
        span.as_unit()
    } else {
        span.as_rounded_unit()
    };
    match span.unit() {
        TimespanUnit::Seconds => tr.scheduling_time_span_seconds(amount),
        TimespanUnit::Minutes => tr.scheduling_time_span_minutes(amount),
        TimespanUnit::Hours => tr.scheduling_time_span_hours(amount),
        TimespanUnit::Days => tr.scheduling_time_span_days(amount),
        TimespanUnit::Months => tr.scheduling_time_span_months(amount),
        TimespanUnit::Years => tr.scheduling_time_span_years(amount),
    }
    .into()
}

const SECOND: f32 = 1.0;
const MINUTE: f32 = 60.0 * SECOND;
const HOUR: f32 = 60.0 * MINUTE;
const DAY: f32 = 24.0 * HOUR;
const YEAR: f32 = 365.0 * DAY;
const MONTH: f32 = YEAR / 12.0;

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub(crate) enum TimespanUnit {
    Seconds,
    Minutes,
    Hours,
    Days,
    Months,
    Years,
}

impl TimespanUnit {
    pub fn as_str(self) -> &'static str {
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
pub(crate) struct Timespan {
    seconds: f32,
    unit: TimespanUnit,
}

impl Timespan {
    pub fn from_secs(seconds: f32) -> Self {
        Timespan {
            seconds,
            unit: TimespanUnit::Seconds,
        }
    }

    /// Return the value as the configured unit, eg seconds=70/unit=Minutes
    /// returns 1.17
    pub fn as_unit(self) -> f32 {
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

    /// Returns the value in the given unit
    pub fn as_provided_unit(&self, unit: TimespanUnit) -> f32 {
        match unit {
            TimespanUnit::Seconds => self.seconds,
            TimespanUnit::Minutes => self.seconds / MINUTE,
            TimespanUnit::Hours => self.seconds / HOUR,
            TimespanUnit::Days => self.seconds / DAY,
            TimespanUnit::Months => self.seconds / MONTH,
            TimespanUnit::Years => self.seconds / YEAR,
        }
    }

    /// Round seconds and days to integers, otherwise
    /// truncates to one decimal place.
    pub fn as_rounded_unit(self) -> f32 {
        match self.unit {
            // seconds/minutes/days as integer
            TimespanUnit::Seconds | TimespanUnit::Days => self.as_unit().round(),
            // other values shown to 1 decimal place
            _ => (self.as_unit() * 10.0).round() / 10.0,
        }
    }

    /// Round seconds, minutes and days to integers, otherwise
    /// truncates to one decimal place.
    pub fn as_rounded_unit_for_answer_buttons(self) -> f32 {
        match self.unit {
            // seconds/minutes/days as integer
            TimespanUnit::Seconds | TimespanUnit::Minutes | TimespanUnit::Days => {
                self.as_unit().round()
            }
            // other values shown to 1 decimal place
            _ => (self.as_unit() * 10.0).round() / 10.0,
        }
    }

    pub fn unit(self) -> TimespanUnit {
        self.unit
    }

    /// Return a new timespan in the most appropriate unit, eg
    /// 70 secs -> timespan in minutes
    pub fn natural_span(self) -> Timespan {
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
    use anki_i18n::I18n;

    use crate::scheduler::timespan::answer_button_time;
    use crate::scheduler::timespan::time_span;
    use crate::scheduler::timespan::MONTH;

    #[test]
    fn answer_buttons() {
        let tr = I18n::template_only();
        assert_eq!(answer_button_time(30.0, &tr), "30s");
        assert_eq!(answer_button_time(70.0, &tr), "1m");
        assert_eq!(answer_button_time(1.1 * MONTH, &tr), "1.1mo");
    }

    #[test]
    fn time_spans() {
        let tr = I18n::template_only();
        assert_eq!(time_span(1.0, &tr, false), "1 second");
        assert_eq!(time_span(30.3, &tr, false), "30 seconds");
        assert_eq!(time_span(30.3, &tr, true), "30.3 seconds");
        assert_eq!(time_span(90.0, &tr, false), "1.5 minutes");
        assert_eq!(time_span(45.0 * 86_400.0, &tr, false), "1.5 months");
        assert_eq!(time_span(364.0 * 86_400.0, &tr, false), "12 months");
        assert_eq!(time_span(364.0 * 86_400.0, &tr, true), "11.97 months");
        assert_eq!(time_span(365.0 * 86_400.0, &tr, false), "1 year");
        assert_eq!(time_span(365.0 * 86_400.0, &tr, true), "1 year");
        assert_eq!(time_span(365.0 * 86_400.0 * 1.5, &tr, false), "1.5 years");
    }
}
