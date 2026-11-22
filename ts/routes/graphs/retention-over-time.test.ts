// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { describe, expect, test } from "vitest";

import type { GraphsResponse } from "@generated/anki/stats_pb";
import { GraphRange } from "./graph-helpers";

import { rollingRetention, targetBand } from "./retention-over-time";

function day(reviews: number, passed: number) {
    return { reviews, passed };
}

describe("rollingRetention", () => {
    test("sums the day and the six days before it", () => {
        const days = new Map([
            [-7, day(10, 0)], // just outside today's window
            [-6, day(4, 2)],
            [0, day(6, 6)],
        ]);
        const points = rollingRetention(days, GraphRange.Month);
        expect(points).toHaveLength(31);
        const today = points[points.length - 1];
        expect(today.day).toBe(0);
        expect(today.reviews).toBe(10);
        expect(today.retention).toBeCloseTo(0.8);
        // a week ago, the window holds -13..-7
        expect(points.find((p) => p.day === -7)!.retention).toBe(0);
    });

    test("leaves gaps where the window has no reviews", () => {
        const points = rollingRetention(new Map([[-20, day(2, 1)]]), GraphRange.Month);
        expect(points.find((p) => p.day === -20)!.retention).toBe(0.5);
        expect(points.find((p) => p.day === -14)!.retention).toBe(0.5);
        expect(points.find((p) => p.day === -13)!.retention).toBeUndefined();
    });

    test("all time starts at the oldest day", () => {
        const points = rollingRetention(new Map([[-400, day(1, 1)]]), GraphRange.AllTime);
        expect(points[0].day).toBe(-400);
    });
});

describe("targetBand", () => {
    test("needs both ends", () => {
        const data = (retentionOverTime: object) => ({ retentionOverTime }) as unknown as GraphsResponse;
        expect(targetBand(data({ days: {}, targetMin: 0.6, targetMax: 0.95 }))).toEqual({
            min: 0.6,
            max: 0.95,
        });
        expect(targetBand(data({ days: {} }))).toBeUndefined();
    });
});
