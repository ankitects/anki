// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "vitest";

import { naturalUnit, naturalWholeUnit, TimespanUnit } from "./time";

test("natural unit", () => {
    expect(naturalUnit(5)).toBe(TimespanUnit.Seconds);
    expect(naturalUnit(59)).toBe(TimespanUnit.Seconds);
    expect(naturalUnit(60)).toBe(TimespanUnit.Minutes);
    expect(naturalUnit(60 * 60 - 1)).toBe(TimespanUnit.Minutes);
    expect(naturalUnit(60 * 60)).toBe(TimespanUnit.Hours);
    expect(naturalUnit(60 * 60 * 24)).toBe(TimespanUnit.Days);
    expect(naturalUnit(60 * 60 * 24 * 31)).toBe(TimespanUnit.Months);
});

test("natural whole unit", () => {
    expect(naturalWholeUnit(5)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(59)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(60)).toBe(TimespanUnit.Minutes);
    expect(naturalWholeUnit(61)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(90)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(60 * 60 - 1)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(60 * 60 + 1)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(60 * 60)).toBe(TimespanUnit.Hours);
    expect(naturalWholeUnit(24 * 60 * 60 - 1)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(24 * 60 * 60 + 1)).toBe(TimespanUnit.Seconds);
    expect(naturalWholeUnit(24 * 60 * 60)).toBe(TimespanUnit.Days);
});
