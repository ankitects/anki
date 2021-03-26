// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { I18n } from "./i18n";

export const SECOND = 1.0;
export const MINUTE = 60.0 * SECOND;
export const HOUR = 60.0 * MINUTE;
export const DAY = 24.0 * HOUR;
export const MONTH = 30.0 * DAY;
export const YEAR = 12.0 * MONTH;

enum TimespanUnit {
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

export function unitAmount(unit: TimespanUnit, secs: number): number {
    switch (unit) {
        case TimespanUnit.Seconds:
            return secs;
        case TimespanUnit.Minutes:
            return secs / MINUTE;
        case TimespanUnit.Hours:
            return secs / HOUR;
        case TimespanUnit.Days:
            return secs / DAY;
        case TimespanUnit.Months:
            return secs / MONTH;
        case TimespanUnit.Years:
            return secs / YEAR;
    }
}

export function studiedToday(i18n: I18n, cards: number, secs: number): string {
    const unit = naturalUnit(secs);
    const amount = unitAmount(unit, secs);
    const name = unitName(unit);

    let secsPer = 0;
    if (cards > 0) {
        secsPer = secs / cards;
    }
    return i18n.statisticsStudiedToday({
        unit: name,
        secsPerCard: secsPer,
        // these two are required, but don't appear in the generated code
        // because they are included as part of a separate term - a byproduct
        // of them having been ported from earlier Qt translations
        cards,
        amount,
    });
}

function i18nFuncForUnit(
    i18n: I18n,
    unit: TimespanUnit,
    short: boolean
): ({ amount: number }) => string {
    if (short) {
        switch (unit) {
            case TimespanUnit.Seconds:
                return i18n.statisticsElapsedTimeSeconds;
            case TimespanUnit.Minutes:
                return i18n.statisticsElapsedTimeMinutes;
            case TimespanUnit.Hours:
                return i18n.statisticsElapsedTimeHours;
            case TimespanUnit.Days:
                return i18n.statisticsElapsedTimeDays;
            case TimespanUnit.Months:
                return i18n.statisticsElapsedTimeMonths;
            case TimespanUnit.Years:
                return i18n.statisticsElapsedTimeYears;
        }
    } else {
        switch (unit) {
            case TimespanUnit.Seconds:
                return i18n.schedulingTimeSpanSeconds;
            case TimespanUnit.Minutes:
                return i18n.schedulingTimeSpanMinutes;
            case TimespanUnit.Hours:
                return i18n.schedulingTimeSpanHours;
            case TimespanUnit.Days:
                return i18n.schedulingTimeSpanDays;
            case TimespanUnit.Months:
                return i18n.schedulingTimeSpanMonths;
            case TimespanUnit.Years:
                return i18n.schedulingTimeSpanYears;
        }
    }
}

/// Describe the given seconds using the largest appropriate unit.
/// If precise is true, show to two decimal places, eg
/// eg 70 seconds -> "1.17 minutes"
/// If false, seconds and days are shown without decimals.
export function timeSpan(i18n: I18n, seconds: number, short = false): string {
    const unit = naturalUnit(seconds);
    const amount = unitAmount(unit, seconds);
    return i18nFuncForUnit(i18n, unit, short).call(i18n, { amount });
}

export function dayLabel(i18n: I18n, daysStart: number, daysEnd: number): string {
    const larger = Math.max(Math.abs(daysStart), Math.abs(daysEnd));
    const smaller = Math.min(Math.abs(daysStart), Math.abs(daysEnd));
    if (larger - smaller <= 1) {
        // singular
        if (daysStart >= 0) {
            return i18n.statisticsInDaysSingle({ days: daysStart });
        } else {
            return i18n.statisticsDaysAgoSingle({ days: -daysStart });
        }
    } else {
        // range
        if (daysStart >= 0) {
            return i18n.statisticsInDaysRange({
                daysStart,
                daysEnd: daysEnd - 1,
            });
        } else {
            return i18n.statisticsDaysAgoRange({
                daysStart: Math.abs(daysEnd - 1),
                daysEnd: -daysStart,
            });
        }
    }
}
