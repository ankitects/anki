// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Copying tags from the tag editor (issue #5995)
 *
 * Ctrl+clicking tags selects them, and Ctrl+C copies the selected tag names to
 * the clipboard, one per line (copySelectedTags() in TagEditor.svelte), with
 * "::" restored in hierarchical tags. The copy also clears the selection.
 *
 * Runs against both the reworked editor and the legacy editor.
 */

import type { Locator, Page } from "@playwright/test";

import { expect, test } from "./fixtures";

test.use({ permissions: ["clipboard-read", "clipboard-write"] });

const TAGS = ["vocab", "lang::french", "verbs"];

/**
 * Loads TAGS into the editor's tag editor and returns it. On the legacy page
 * the globals belong to the second NoteEditor, so the last one is used.
 */
async function loadTags(page: Page): Promise<Locator> {
    await page.evaluate((tags) => {
        const w = window as any;
        w.setTags(tags);
        w.setTagsCollapsed(false);
    }, TAGS);
    const tagEditor = page.locator(".note-editor").last().locator(".tag-editor");
    for (const name of TAGS) {
        await expect(tagButton(tagEditor, name)).toBeVisible();
    }
    return tagEditor;
}

/** The tag editor shows "::" as "∷" (replaceWithUnicodeSeparator() in tags.ts). */
function tagButton(tagEditor: Locator, name: string): Locator {
    const shown = name.replace(/::/g, "∷");
    return tagEditor.locator(`button.tag[data-addon-tag="${shown}"]`);
}

async function readClipboard(page: Page): Promise<string> {
    return page.evaluate(() => navigator.clipboard.readText());
}

async function expectCopiesSelectedTags(page: Page): Promise<void> {
    const tagEditor = await loadTags(page);
    await page.evaluate(() => navigator.clipboard.writeText(""));

    await tagButton(tagEditor, "vocab").click({ modifiers: ["Control"] });
    await tagButton(tagEditor, "lang::french").click({ modifiers: ["Control"] });
    await expect(tagButton(tagEditor, "vocab")).toHaveClass(/\bselected\b/);
    await expect(tagButton(tagEditor, "lang::french")).toHaveClass(/\bselected\b/);
    await expect(tagButton(tagEditor, "verbs")).not.toHaveClass(/\bselected\b/);

    await page.keyboard.press("Control+c");

    await expect.poll(() => readClipboard(page)).toBe("vocab\nlang::french");
    await expect(tagEditor.locator("button.tag.selected")).toHaveCount(0);
}

test("Ctrl+C copies the selected tags in the editor", async ({ editor: page }) => {
    await expectCopiesSelectedTags(page);
});

test("Ctrl+C copies the selected tags in the legacy editor", async ({ legacyEditor: page }) => {
    await expectCopiesSelectedTags(page);
});
