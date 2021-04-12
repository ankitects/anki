// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "./i18n";

export const SECOND = 1.0;
export const MINUTE = 60.0 * SECOND;
export const HOUR = 60.0 * MINUTE;
export const DAY = 24.0 * HOUR;
export const MONTH = 30.0 * DAY;
export const YEAR = 12.0 * MONTH;

export enum TimespanUnit {
    Seconds,
    Minutes,
    Hours,
    Days,
    Months,
    Years,
}

export function unitName(unit: TimespanUnit): string {
    switch (unit) {
        case TimespanUnit.Seconds:
            return "seconds";
        case TimespanUnit.Minutes:
            return "minutes";
        case TimespanUnit.Hours:
            return "hours";
        case TimespanUnit.Days:
            return "days";
        case TimespanUnit.Months:
            return "months";
        case TimespanUnit.Years:
            return "years";
    }
}

export function naturalUnit(secs: number): TimespanUnit {
    secs = Math.abs(secs);
    if (secs < MINUTE) {
        return TimespanUnit.Seconds;
    } else if (secs < HOUR) {
        return TimespanUnit.Minutes;
    } else if (secs < DAY) {
        return TimespanUnit.Hours;
    } else if (secs < MONTH) {
        return TimespanUnit.Days;
    } else if (secs < YEAR) {
        return TimespanUnit.Months;
    } else {
        return TimespanUnit.Years;
    }
}

/// Number of seconds in a given unit.
export function unitSeconds(unit: TimespanUnit): number {
    switch (unit) {
        case TimespanUnit.Seconds:
            return SECOND;
        case TimespanUnit.Minutes:
            return MINUTE;
        case TimespanUnit.Hours:
            return HOUR;
        case TimespanUnit.Days:
            return DAY;
        case TimespanUnit.Months:
            return MONTH;
        case TimespanUnit.Years:
            return YEAR;
    }
}

export function unitAmount(unit: TimespanUnit, secs: number): number {
    return secs / unitSeconds(unit);
}

/// Largest unit provided seconds can be divided by without a remainder.
export function naturalWholeUnit(secs: number): TimespanUnit {
    let unit = naturalUnit(secs);
    while (unit != TimespanUnit.Seconds) {
        const amount = Math.round(unitAmount(unit, secs));
        if (Math.abs(secs - amount * unitSeconds(unit)) < Number.EPSILON) {
            return unit;
        }
        unit -= 1;
    }
    return unit;
}

export function studiedToday(cards: number, secs: number): string {
    const unit = naturalUnit(secs);
    const amount = unitAmount(unit, secs);
    const name = unitName(unit);

    let secsPer = 0;
    if (cards > 0) {
        secsPer = secs / cards;
    }
    return tr.statisticsStudiedToday({
        unit: name,
        secsPerCard: secsPer,
        cards,
        amount,
    });
}

function i18nFuncForUnit(
    unit: TimespanUnit,
    short: boolean
): ({ amount: number }) => string {
    if (short) {
        switch (unit) {
            case TimespanUnit.Seconds:
                return tr.statisticsElapsedTimeSeconds;
            case TimespanUnit.Minutes:
                return tr.statisticsElapsedTimeMinutes;
            case TimespanUnit.Hours:
                return tr.statisticsElapsedTimeHours;
            case TimespanUnit.Days:
                return tr.statisticsElapsedTimeDays;
            case TimespanUnit.Months:
                return tr.statisticsElapsedTimeMonths;
            case TimespanUnit.Years:
                return tr.statisticsElapsedTimeYears;
        }
    } else {
        switch (unit) {
            case TimespanUnit.Seconds:
                return tr.schedulingTimeSpanSeconds;
            case TimespanUnit.Minutes:
                return tr.schedulingTimeSpanMinutes;
            case TimespanUnit.Hours:
                return tr.schedulingTimeSpanHours;
            case TimespanUnit.Days:
                return tr.schedulingTimeSpanDays;
            case TimespanUnit.Months:
                return tr.schedulingTimeSpanMonths;
            case TimespanUnit.Years:
                return tr.schedulingTimeSpanYears;
        }
    }
}

/// Describe the given seconds using the largest appropriate unit.
/// If precise is true, show to two decimal places, eg
/// eg 70 seconds -> "1.17 minutes"
/// If false, seconds and days are shown without decimals.
export function timeSpan(seconds: number, short = false): string {
    const unit = naturalUnit(seconds);
    const amount = unitAmount(unit, seconds);
    return i18nFuncForUnit(unit, short)({ amount });
}

export function dayLabel(daysStart: number, daysEnd: number): string {
    const larger = Math.max(Math.abs(daysStart), Math.abs(daysEnd));
    const smaller = Math.min(Math.abs(daysStart), Math.abs(daysEnd));
    if (larger - smaller <= 1) {
        // singular
        if (daysStart >= 0) {
            return tr.statisticsInDaysSingle({ days: daysStart });
        } else {
            return tr.statisticsDaysAgoSingle({ days: -daysStart });
        }
    } else {
        // range
        if (daysStart >= 0) {
            return tr.statisticsInDaysRange({
                daysStart,
                daysEnd: daysEnd - 1,
            });
        } else {
            return tr.statisticsDaysAgoRange({
                daysStart: Math.abs(daysEnd - 1),
                daysEnd: -daysStart,
            });
        }
    }
}
