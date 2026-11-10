// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "vitest";
import {
    recordSimulation,
    simulationHistoryCsv,
    simulationTotals,
} from "./simulator-history";

const settings = {
    daysToSimulate: 2,
    deckSize: 25,
    desiredRetention: 0.9,
    newLimit: 10,
    reviewLimit: 100,
};

test("recording a simulation keeps a snapshot of inputs and backend results", () => {
    const newCounts = [2, 3];
    const reviewCounts = [10, 12];
    const run = recordSimulation(1, settings, newCounts, reviewCounts);
    settings.newLimit = 500;
    newCounts[0] = 999;
    reviewCounts[1] = 999;
    expect(run.newLimit).toBe(10);
    expect(run.dailyNewCount).toEqual([2, 3]);
    expect(run.dailyReviewCount).toEqual([10, 12]);
    expect(simulationTotals(run)).toEqual({ newCards: 5, reviews: 22, total: 27 });
});

test("CSV export includes each run's days and unsmoothed new/review totals", () => {
    const run1 = recordSimulation(1, { ...settings, newLimit: 10 }, [2, 3], [10, 12]);
    const run2 = recordSimulation(2, { ...settings, desiredRetention: 0.95 }, [4], [5, 6]);
    expect(simulationHistoryCsv([run1, run2])).toBe(
        "run,day,new,reviews,total,desired_retention,new_limit,review_limit,additional_new_cards,days_to_simulate\r\n" +
        "1,1,2,10,12,0.9,10,100,25,2\r\n" +
        "1,2,3,12,15,0.9,10,100,25,2\r\n" +
        "2,1,4,5,9,0.95,10,100,25,2\r\n" +
        "2,2,0,6,6,0.95,10,100,25,2\r\n",
    );
});

test("empty history exports only the CSV header", () => {
    expect(simulationHistoryCsv([])).toBe(
        "run,day,new,reviews,total,desired_retention,new_limit,review_limit,additional_new_cards,days_to_simulate\r\n",
    );
});
