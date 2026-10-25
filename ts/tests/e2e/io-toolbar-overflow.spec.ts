// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Regression test for #5779: the mask editor's top toolbar sits in a `1fr`
 * grid column, which could not shrink below the width of all the toolbar
 * buttons. In a narrow window the toolbar overflowed the page instead of
 * scrolling.
 *
 * Adds the image to the media folder. No note is added, so the current
 * notetype stays as it was.
 */

import path from "path";

import { expect, test } from "./fixtures";
import { openChooserAndSelect } from "./helpers";

const TEST_IMAGE = path.resolve("qt/aqt/data/web/imgs/anki-logo-thin.png");

test("mask editor toolbar scrolls in a narrow window", async ({ editor: page }) => {
    await openChooserAndSelect(page, "notetype", "Image Occlusion");
    await page.evaluate(
        (imagePath) => (globalThis as any).setupMaskEditorForNewNote(imagePath),
        TEST_IMAGE,
    );
    const toolbar = page.locator(".top-tool-bar-container");
    await expect(toolbar).toBeVisible();

    await page.setViewportSize({ width: 360, height: 700 });

    const layout = await toolbar.evaluate((el) => ({
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
        right: el.getBoundingClientRect().right,
        viewportWidth: window.innerWidth,
    }));
    expect(layout.right).toBeLessThanOrEqual(layout.viewportWidth);
    expect(layout.scrollWidth).toBeGreaterThan(layout.clientWidth);
});
