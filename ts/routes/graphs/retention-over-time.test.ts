// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { describe, expect, test } from "vitest";

import { GraphRange } from "./graph-helpers";
import { rollingRetention } from "./retention-over-time";

function day(reviews: number, passed: number, predictedReviews = 0, predictedSum = 0) {
    return { reviews, passed, predictedReviews, predictedSum };
}

describe("rollingRetention", () => {
    test("sums the day and the six days before it", () => {
        const days = new Map([
            [-7, day(10, 0)], // just outside today's window
            [-6, day(4, 2, 4, 3.2)],
            [0, day(6, 6, 2, 1.8)],
        ]);
        const points = rollingRetention(days, GraphRange.Month);
        expect(points).toHaveLength(31);
        const today = points[points.length - 1];
        expect(today.day).toBe(0);
        expect(today.reviews).toBe(10);
        expect(today.actual).toBeCloseTo(0.8);
        expect(today.predicted).toBeCloseTo(5 / 6);
        // a week ago, the window holds -13..-7
        const weekAgo = points.find((p) => p.day === -7)!;
        expect(weekAgo.actual).toBe(0);
        expect(weekAgo.predicted).toBeUndefined();
    });

    test("leaves gaps where the window has no reviews", () => {
        const points = rollingRetention(new Map([[-20, day(2, 1)]]), GraphRange.Month);
        expect(points.find((p) => p.day === -20)!.actual).toBe(0.5);
        expect(points.find((p) => p.day === -14)!.actual).toBe(0.5);
        expect(points.find((p) => p.day === -13)!.actual).toBeUndefined();
    });

    test("all time starts at the oldest day", () => {
        const points = rollingRetention(new Map([[-400, day(1, 1)]]), GraphRange.AllTime);
        expect(points[0].day).toBe(-400);
    });
});
