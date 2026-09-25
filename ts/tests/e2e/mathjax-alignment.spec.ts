// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * MathJax survives text alignment (issue #5176)
 *
 * Chromium's justify* commands end the paragraph at the contenteditable=false
 * <anki-mathjax> element and move the paragraph, including the adjacent frame
 * handle, into a new block. The frame used to treat the missing handle as a
 * deletion and removed the whole MathJax element.
 *
 * Content is seeded with setFields() the way editor_legacy.py loads a note, as
 * that is the path that decorates `\[...\]` into <anki-mathjax>.
 */

import type { Locator, Page } from "@playwright/test";

import { expect, test } from "./fixtures";
import { editableField } from "./helpers";

async function seedFirstField(page: Page, html: string): Promise<Locator> {
    await page.evaluate(
        (h: string) => (window as any).setFields(["Front", "Back"], [h, ""]),
        html,
    );
    // The legacy fixture mounts a second NoteEditor; use its field.
    const field = editableField(page, 0).last();
    await expect(field.locator("anki-mathjax[decorated]")).toBeAttached({ timeout: 10_000 });
    return field;
}

async function placeCaretInFirstText(field: Locator, offset: number): Promise<void> {
    // Click on the first line rather than the centre of the field, which
    // might hit the MathJax and open its editor instead.
    await field.click({ position: { x: 5, y: 12 } });
    await field.evaluate((el, offset) => {
        const selection = (el.getRootNode() as ShadowRoot).getSelection()!;
        const range = document.createRange();
        range.setStart(el.firstChild!, offset);
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);
    }, offset);
}

async function alignCenter(page: Page): Promise<void> {
    await page.getByTitle("Alignment").last().click();
    await page.getByTitle("Center").last().click();
}

async function expectIntactFrame(field: Locator, mathjax: string): Promise<void> {
    const frame = field.locator("anki-frame");
    await expect(frame).toHaveCount(1);
    await expect(frame.locator("anki-mathjax")).toHaveAttribute("data-mathjax", mathjax);
    await expect(frame.locator("frame-start")).toHaveText(" ");
    await expect(frame.locator("frame-end")).toHaveText(" ");
}

test("centering the line above a block MathJax keeps the MathJax", async ({ legacyEditor: page }) => {
    const field = await seedFirstField(page, "Lorem ipsum dolor\\[abc\\]");
    await placeCaretInFirstText(field, 3);

    await alignCenter(page);

    await expect(field.locator("div[style*='text-align: center']")).toHaveText("Lorem ipsum dolor");
    await expectIntactFrame(field, "abc");
});

test("centering a line containing inline MathJax keeps the MathJax", async ({ legacyEditor: page }) => {
    const field = await seedFirstField(page, "Lorem \\(abc\\) dolor");
    await placeCaretInFirstText(field, 3);

    await alignCenter(page);

    await expectIntactFrame(field, "abc");
});

test("centering the line below a block MathJax keeps the MathJax", async ({ legacyEditor: page }) => {
    const field = await seedFirstField(page, "Lorem ipsum dolor\\[abc\\]tail");
    await placeCaretInFirstText(field, 0);
    await page.keyboard.press("Control+End");

    await alignCenter(page);

    await expect(field.locator("div[style*='text-align: center']")).toHaveText("tail");
    await expectIntactFrame(field, "abc");
});

test("backspace after inline MathJax still deletes it", async ({ legacyEditor: page }) => {
    const field = await seedFirstField(page, "Lorem \\(abc\\)");
    const box = (await field.boundingBox())!;
    // Click behind the MathJax, i.e. into the trailing frame handle.
    await field.click({ position: { x: box.width - 10, y: 12 } });

    await page.keyboard.press("Backspace");

    await expect(field.locator("anki-mathjax")).toHaveCount(0);
    await expect(field.locator("anki-frame")).toHaveCount(0);
    await expect(field).toHaveText("Lorem ");
});
