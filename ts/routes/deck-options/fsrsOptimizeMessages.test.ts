// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test, vi } from "vitest";

vi.mock("@generated/ftl", () => ({
    deckConfigFsrsParamsUsingDefault: () => "using default",
    deckConfigFsrsParamsOptimal: () => "optimal",
    deckConfigFsrsReviewsIgnoredByOptimizer: () => "ignored",
    deckConfigFsrsParamsNoReviews: () => "no reviews",
}));

import { getFsrsAlreadyOptimalMessage, parametersEqual } from "./fsrsOptimizeMessages";

test("parametersEqual compares values at 4 decimal precision", () => {
    expect(parametersEqual([0.123456, 0.4], [0.123454, 0.4])).toBe(true);
});

test("parametersEqual returns false when one value differs at 4 decimals", () => {
    expect(parametersEqual([0.12344, 0.4], [0.12355, 0.4])).toBe(false);
});

test("parametersEqual returns false when arrays have different lengths", () => {
    expect(parametersEqual([0.1, 0.2], [0.1])).toBe(false);
});

test("parametersEqual returns true with empty includeEmptyCheck and matching non-empty arrays", () => {
    expect(parametersEqual([0.1, 0.2], [0.1, 0.2])).toBe(true);
});

test("already-optimal message is empty when not already optimal", () => {
    const message = getFsrsAlreadyOptimalMessage(false, true, 1, 5);
    const message2 = getFsrsAlreadyOptimalMessage(false, false, 1, 5);

    expect(message).toBe("");
    expect(message2).toBe("");
});

test("already-optimal message uses default string when fsrsItems and default", () => {
    const message = getFsrsAlreadyOptimalMessage(true, true, 1, 5);

    expect(message).toBe("using default");
});

test("already-optimal message uses optimal string when fsrsItems and not default", () => {
    const message = getFsrsAlreadyOptimalMessage(true, false, 1, 5);

    expect(message).toBe("optimal");
});

test("already-optimal message reports ignored reviews when no fsrsItems and revlogs exist", () => {
    const message = getFsrsAlreadyOptimalMessage(true, false, 0, 2);

    expect(message).toBe("ignored");
});

test("already-optimal message reports no reviews when no fsrsItems and no revlogs", () => {
    const message = getFsrsAlreadyOptimalMessage(true, false, 0, 0);

    expect(message).toBe("no reviews");
});
