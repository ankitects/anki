// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { naturalWholeUnit, TimespanUnit, unitAmount, unitSeconds } from "@tslib/time";

function unitSuffix(unit: TimespanUnit): string {
    switch (unit) {
        case TimespanUnit.Seconds:
            return "s";
        case TimespanUnit.Minutes:
            return "m";
        case TimespanUnit.Hours:
            return "h";
        case TimespanUnit.Days:
            return "d";
        default:
            // should not happen
            return "";
    }
}

function suffixToUnit(suffix: string): TimespanUnit {
    switch (suffix) {
        case "s":
            return TimespanUnit.Seconds;
        case "h":
            return TimespanUnit.Hours;
        case "d":
            return TimespanUnit.Days;
        default:
            return TimespanUnit.Minutes;
    }
}

function minutesToString(step: number): string {
    const secs = step * 60;
    let unit = naturalWholeUnit(secs);
    if ([TimespanUnit.Months, TimespanUnit.Years].includes(unit)) {
        unit = TimespanUnit.Days;
    }
    const amount = Math.round(unitAmount(unit, secs));

    return `${amount}${unitSuffix(unit)}`;
}

function stringToMinutes(text: string): number {
    const match = text.match(/(\d+)(.*)/);
    if (match) {
        const [_, num, suffix] = match;
        const unit = suffixToUnit(suffix);
        const seconds = unitSeconds(unit) * parseInt(num, 10);
        // should be representable as negative i32 seconds in a revlog
        const capped_seconds = Math.min(seconds, 2 ** 31);
        return capped_seconds / 60;
    } else {
        return 0;
    }
}

export function stepsToString(steps: number[]): string {
    return steps.map(minutesToString).join(" ");
}

export function stringToSteps(text: string): number[] {
    return (
        text
            .split(" ")
            .map(stringToMinutes)
            // remove zeros
            .filter((e) => e)
    );
}
