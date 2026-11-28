// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Tooltips rendered through WithTooltip.svelte treat their content as text.
 */

import { expect, test } from "./fixtures";

const MARKUP_TAG = "parent::<b>bold</b>";

/**
 * Enough tags to wrap the tag editor past two rows, which makes it shorten
 * hierarchical tags.
 */
const FILLER_TAGS = Array.from({ length: 60 }, (_, i) => `filler-tag-${i}`);

test("tag tooltip shows markup in the tag name as text", async ({ editor: page }) => {
    await page.evaluate((tags) => {
        const w = window as any;
        w.setTags(tags);
        w.setTagsCollapsed(false);
    }, [MARKUP_TAG, ...FILLER_TAGS]);
    const tag = page.locator(`button.tag[data-addon-tag="parent∷<b>bold</b>"]`);
    await expect(tag).toHaveText("…∷<b>bold</b>");

    await tag.hover();

    const tooltip = page.locator(".tooltip .tooltip-inner");
    await expect(tooltip).toHaveText("parent∷<b>bold</b>");
    await expect(tooltip.locator("b")).toHaveCount(0);
});
