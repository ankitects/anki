// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "./fixtures";
import { editableField, isRpcResponse } from "./helpers";

test("a graph tooltip does not widen the page after the viewport narrows", async ({ editor: page }) => {
    // graphs have nothing to hover in an empty collection
    await editableField(page, 0).click();
    await editableField(page, 0).pressSequentially("graph tooltip");
    const added = page.waitForResponse(isRpcResponse("addNote"));
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await added;

    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto("/graphs");
    // the last bin can be empty, and has no width
    await page.locator(".hover-columns rect").filter({ visible: true }).last().hover();
    await expect(page.locator(".tooltip")).toHaveCSS("opacity", "1");

    await page.setViewportSize({ width: 400, height: 800 });

    await expect
        .poll(() => page.evaluate(() => document.documentElement.scrollWidth))
        .toBe(400);
});
