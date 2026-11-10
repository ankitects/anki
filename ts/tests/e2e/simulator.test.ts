// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { SimulateFsrsReviewRequest, SimulateFsrsReviewResponse } from "@generated/anki/scheduler_pb";
import { expect, test } from "../../tests/e2e/fixtures";
import { callRpc } from "./helpers";

function simulateFsrsReview(page: any, request: SimulateFsrsReviewRequest): Promise<SimulateFsrsReviewResponse> {
    return callRpc(page, "simulateFsrsReview", request).then((responseBytes) => {
        return SimulateFsrsReviewResponse.fromBinary(responseBytes);
    });
}

test("learning steps passed to simulation", async ({ page }) => {
    await page.goto("/deck-options/1");

    const simulateFsrsRequest = new SimulateFsrsReviewRequest({
        desiredRetention: 0.9,
        newLimit: 10,
        reviewLimit: 9999,
        maxInterval: 365,
        search: "nothing",
        deckSize: 10,
        newCardsIgnoreReviewLimit: false,
        historicalRetention: 0.9,
        learningStepCount: 1,
        daysToSimulate: 1,
    });

    const resp = await simulateFsrsReview(page, simulateFsrsRequest);

    expect(resp).toBeDefined();
    const one_step_cost = resp!.dailyTimeCost[0];

    simulateFsrsRequest.learningStepCount = 2;
    const resp2 = await simulateFsrsReview(page, simulateFsrsRequest);
    expect(resp2).toBeDefined();
    const two_step_cost = resp2!.dailyTimeCost[0];

    expect(two_step_cost).toBeGreaterThan(one_step_cost);
});
