// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "vitest";

import { stepsToString, stringToSteps } from "./steps";

test("whole steps", () => {
    const steps = [1, 10, 60, 120, 1440];
    const string = "1m 10m 1h 2h 1d";
    expect(stepsToString(steps)).toBe(string);
    expect(stringToSteps(string)).toStrictEqual(steps);
});

test("fractional steps", () => {
    const steps = [1 / 60, 5 / 60, 1.5, 400];
    const string = "1s 5s 90s 400m";
    expect(stepsToString(steps)).toBe(string);
    expect(stringToSteps(string)).toStrictEqual(steps);
});

test("rounding", () => {
    const steps = [0.1666666716337204];
    expect(stepsToString(steps)).toBe("10s");
});

test("parsing", () => {
    expect(stringToSteps("")).toStrictEqual([]);
    expect(stringToSteps("    ")).toStrictEqual([]);
    expect(stringToSteps("1 hello 2")).toStrictEqual([1, 2]);
});
