// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test, vi } from "vitest";

import type { GraphData } from "./future-due";
import { buildHistogram } from "./future-due";
import { GraphRange } from "./graph-helpers";

function dueIn(...days: number[]): GraphData {
    return {
        dueCounts: new Map(days.map((day) => [day, 1])),
        haveBacklog: days.some((day) => day < 0),
        dailyLoad: 0,
    };
}

function binHolding(day: number, data: GraphData, range: GraphRange, includeBacklog = false) {
    const { histogramData } = buildHistogram(data, range, includeBacklog, vi.fn(), true);
    const bin = histogramData!.bins.find((bin) => (bin as unknown as [number, number][]).some(([d]) => d === day));
    return { x0: bin?.x0, x1: bin?.x1 };
}

test.each([
    { range: GraphRange.Month, lastDay: 31, expected: { x0: 30, x1: 31 } },
    { range: GraphRange.ThreeMonths, lastDay: 90, expected: { x0: 89, x1: 90 } },
    { range: GraphRange.Year, lastDay: 365, expected: { x0: 360, x1: 365 } },
    { range: GraphRange.AllTime, lastDay: 110, expected: { x0: 108, x1: 110 } },
])("a card due on the last day of the range ($lastDay) is in a full-width bin", ({ range, lastDay, expected }) => {
    expect(binHolding(lastDay, dueIn(2, lastDay), range)).toEqual(expected);
});

test("a card due just past the last tick of the all range shares the last full-width bin", () => {
    expect(binHolding(2001, dueIn(2, 2001), GraphRange.AllTime)).toEqual({ x0: 1980, x1: 2001 });
});

test("an overdue card just before the first tick shares the first full-width bin", () => {
    expect(binHolding(-51, dueIn(-51, 110), GraphRange.AllTime, true)).toEqual({ x0: -51, x1: -48 });
});
