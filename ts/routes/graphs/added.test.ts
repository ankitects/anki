// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test, vi } from "vitest";

import type { GraphData } from "./added";
import { buildHistogram } from "./added";
import { GraphRange } from "./graph-helpers";

function addedOn(...days: number[]): GraphData {
    return { daysAdded: new Map(days.map((day) => [day, 1])) };
}

function binHolding(day: number, data: GraphData, range: GraphRange, dispatch = vi.fn()) {
    const [histogramData] = buildHistogram(data, range, dispatch, true);
    return histogramData!.bins.find((bin) => (bin as unknown as [number, number][]).some(([d]) => d === day))!;
}

test.each([
    { range: GraphRange.Year, oldest: -100, expected: { x0: -5, x1: 1 } },
    { range: GraphRange.AllTime, oldest: -20727, expected: { x0: -200, x1: 1 } },
])("a card added today is in a full-width bin", ({ range, oldest, expected }) => {
    const bin = binHolding(0, addedOn(oldest, 0), range);

    expect({ x0: bin.x0, x1: bin.x1 }).toEqual(expected);
});

test("the oldest card in the all range is in a full-width bin", () => {
    const bin = binHolding(-20601, addedOn(-20601, -100), GraphRange.AllTime);

    expect({ x0: bin.x0, x1: bin.x1 }).toEqual({ x0: -20601, x1: -20400 });
});

test("clicking the newest bin searches up to and including today", () => {
    const dispatch = vi.fn();
    const [histogramData] = buildHistogram(addedOn(-100, -7, 0), GraphRange.Year, dispatch, true);
    const binOf = (day: number) =>
        histogramData!.bins.find((bin) => (bin as unknown as [number, number][]).some(([d]) => d === day))!;

    histogramData!.onClick!(binOf(0));
    histogramData!.onClick!(binOf(-7));

    expect(dispatch.mock.calls).toEqual([
        ["search", { query: "\"added:6\"" }],
        ["search", { query: "\"added:11\" AND -\"added:6\"" }],
    ]);
});
