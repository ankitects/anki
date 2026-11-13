// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test } from "./fixtures";
import { editableField } from "./helpers";

test("pressing backspace after a mathjax frame deletes it and undo restores it", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await field.click();

    // Type the mathjax tag directly in HTML source view since Mathjax editor is too finicky to use for tests.
    await page.keyboard.press("ControlOrMeta+Shift+x");
    await page.keyboard.type("<anki-mathjax>x^2</anki-mathjax>");
    await page.keyboard.press("ControlOrMeta+Shift+x");

    // Element should exist
    await expect.poll(async () => field.evaluate((el) => el.innerHTML)).toContain("<anki-mathjax");
    await expect.poll(async () => field.evaluate((el) => el.innerHTML)).toContain("x^2");

    // Backspace deletes the entire MathJax element
    await page.keyboard.press("Backspace");
    await expect.poll(async () => field.evaluate((el) => el.innerHTML)).not.toContain("<anki-mathjax");

    // Undo restores the element and its formula
    await page.keyboard.press("ControlOrMeta+z");
    await expect.poll(async () => field.evaluate((el) => el.innerHTML)).toContain("<anki-mathjax");
    await expect.poll(async () => field.evaluate((el) => el.innerHTML)).toContain("x^2");
});
