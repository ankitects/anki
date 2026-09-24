// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test, vi } from "vitest";

import { prepareData } from "./forgetting-curve";

const FSRS7_PARAMS = [
    0.1104,
    2.2395,
    3.9221,
    11.7841,
    6.1686,
    0.6457,
    3.6807,
    1.9795,
    0.0,
    1.3826,
    0.7024,
    0.5999,
    0.8146,
    0.6398,
    1.0,
    1.3207,
    0.6707,
    3.8668,
    0.4416,
    0.0934,
    1.8631,
    0.6162,
    1.0869,
    0.1567,
    0.0801,
    0.2421,
    0.9464,
    0.1433,
    0.7145,
    0.0,
    0.5667,
    0.3734,
    0.5333,
    0.3048,
];

test("FSRS-7 forgetting curve uses the complete memory state", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2024-01-21T00:00:00Z"));

    try {
        const data = prepareData(
            [
                {
                    time: BigInt(Date.parse("2024-01-01T00:00:00Z") / 1000),
                    memoryState: {
                        stability: 10,
                        stabilityInternal: 10,
                        stabilityFast: 5,
                        difficulty: 8,
                    },
                },
            ] as never,
            20,
            0.1542,
            FSRS7_PARAMS,
        );

        expect(data.at(-1)?.retrievability).toBeCloseTo(82.88255, 4);
    } finally {
        vi.useRealTimers();
    }
});
