// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "./fixtures";

test("mediasrv is reachable", async ({ page }) => {
    const response = await page.goto("/favicon.ico");
    expect(response?.status()).toBe(200);
});

test("congrats SvelteKit page loads", async ({ page }) => {
    await page.goto("/congrats");
    await expect(page.locator("body")).toBeAttached();
});
