// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { DeckConfigsForUpdate } from "@generated/anki/deck_config_pb";
import {
    ComputeFsrsParamsRequest,
    ComputeFsrsParamsResponse,
    EvaluateParamsLegacyRequest,
    EvaluateParamsResponse,
} from "@generated/anki/scheduler_pb";

import { expect, test } from "./fixtures";
import { decodeRequestBody } from "./helpers";

test("empty FSRS parameters evaluate and optimize as FSRS7 without selectors", async ({ page }) => {
    let defaults: number[] = [];
    const evaluations: number[][] = [];
    await page.route("**/_anki/getDeckConfigsForUpdate", async (route) => {
        const response = await route.fetch();
        const data = DeckConfigsForUpdate.fromBinary(await response.body());
        defaults = data.defaults!.config!.fsrsParams7;
        data.fsrs = true;
        data.fsrsLegacyEvaluate = true;
        data.fsrsHealthCheck = false;
        const config = data.allConfig.find((entry) => entry.config!.id === data.currentDeck!.configId)!
            .config!.config!;
        config.fsrsParams4 = [];
        config.fsrsParams5 = [];
        config.fsrsParams6 = [];
        config.fsrsParams7 = [];
        await route.fulfill({ response, body: Buffer.from(data.toBinary()) });
    });
    await page.route("**/_anki/computeFsrsParams", async (route) => {
        const request = decodeRequestBody(route.request(), ComputeFsrsParamsRequest);
        expect(request.currentParams).toEqual([]);
        expect(defaults).toHaveLength(34);
        await route.fulfill({
            body: Buffer.from(new ComputeFsrsParamsResponse({ params: defaults, fsrsItems: 100 }).toBinary()),
        });
    });
    await page.route("**/_anki/evaluateParamsLegacy", async (route) => {
        evaluations.push(decodeRequestBody(route.request(), EvaluateParamsLegacyRequest).params);
        await route.fulfill({
            body: Buffer.from(new EvaluateParamsResponse({ logLoss: 0.4689, rmseBins: 0.0581 }).toBinary()),
        });
    });
    await page.goto("/deck-options/1");
    const parameters = page.getByRole("button", { name: "FSRS Parameters", exact: true }).locator("textarea");
    await expect(parameters).toHaveValue("");
    async function evaluate(): Promise<void> {
        const dialog = page.waitForEvent("dialog");
        await page.getByRole("button", { name: "Evaluate", exact: true }).click();
        await (await dialog).accept();
    }
    // Empty arrays select FSRS7 in the backend; no extra version field or
    // fabricated legacy defaults are needed in this production UI.
    await evaluate();
    expect(evaluations).toEqual([[]]);
    await page.getByRole("button", { name: "Optimize Current Preset", exact: true }).click();
    await expect(parameters).not.toHaveValue("");
    await evaluate();
    expect(evaluations[1]).toEqual(defaults);
    await expect(page.getByText("New card intervals at graduation", { exact: true })).toBeVisible();
});
