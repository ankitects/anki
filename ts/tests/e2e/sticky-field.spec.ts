// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Suite 2 – sticky field toggle (issue #4930)
 *
 * Verifies that the new TypeScript sticky path (StickyBadge.svelte, non-legacy
 * branch) calls getNotetype + updateNotetype RPCs instead of the legacy
 * bridgeCommand("toggleSticky:N").
 *
 * Assertions:
 *  - /_anki/getNotetype fires when the badge is clicked.
 *  - /_anki/updateNotetype fires; decoded Notetype has fields[0].config.sticky
 *    flipped to true.
 *  - The badge gains the "highlighted" CSS class (StickyBadge.svelte:56).
 *  - window.__bridgeCalls does NOT contain any "toggleSticky" command (proves
 *    the legacy bridgeCommand path was not taken).
 *  - A second click flips sticky back to false and removes "highlighted".
 *
 * This test mutates the notetype configuration. It toggles twice so the final
 * state matches the initial state. If a downstream test depends on a specific
 * sticky state, set it explicitly.
 */

import { Notetype } from "@generated/anki/notetypes_pb";

import { expect, test } from "./fixtures";
import { bridgeCalls, decodeRequestBody, fieldContainer, isRpc } from "./helpers";

test("clicking sticky badge uses getNotetype+updateNotetype, not legacy bridgeCommand", async ({ editor: page }) => {
    const container = fieldContainer(page, 0);

    // StickyBadge is visible only when its parent field is hovered or focused.
    // Hover first so show=true.
    await container.hover();

    // DOM layout inside .field-container (LabelContainer.svelte):
    //   span.collapse-label[role="button"]   ← CollapseLabel (aria-expanded)
    //   span.field-state                     ← FieldState (default slot)
    //     span[role="button"]                ← StickyBadge  ← we want this
    //     span[role="button"]                ← PlainTextBadge/RichTextBadge
    //
    // Scoping to .field-state skips the CollapseLabel and picks StickyBadge.
    const badge = container.locator('.field-state [role="button"]').first();
    await expect(badge).toBeVisible({ timeout: 5_000 });

    // Capture both RPCs BEFORE clicking so no race condition.
    const getNotetypeReqPromise = page.waitForRequest(isRpc("getNotetype"), { timeout: 10_000 });
    const updateNotetypeReqPromise = page.waitForRequest(isRpc("updateNotetype"), { timeout: 10_000 });

    await badge.click();

    // Both RPCs must have fired.
    await getNotetypeReqPromise;
    const updateNotetypeReq = await updateNotetypeReqPromise;

    // Decode the updateNotetype payload and assert sticky was set.
    const notetype = decodeRequestBody(updateNotetypeReq, Notetype);
    expect(notetype.fields[0].config?.sticky).toBe(true);

    // Badge must gain the "highlighted" class (StickyBadge.svelte:56).
    await expect(badge).toHaveClass(/highlighted/, { timeout: 5_000 });

    // The legacy toggleSticky bridgeCommand must NOT have been used.
    const calls = await bridgeCalls(page);
    expect(calls.some((c) => c.startsWith("toggleSticky"))).toBe(false);

    // --- Toggle back so the notetype is restored ---

    // Re-hover to ensure show=true (hover may have been lost after the first click).
    await container.hover();
    await expect(badge).toBeVisible({ timeout: 5_000 });

    const updateNotetypeReq2Promise = page.waitForRequest(isRpc("updateNotetype"), { timeout: 10_000 });

    await badge.click();

    const updateNotetypeReq2 = await updateNotetypeReq2Promise;
    const notetype2 = decodeRequestBody(updateNotetypeReq2, Notetype);
    expect(notetype2.fields[0].config?.sticky).toBe(false);
    await expect(badge).not.toHaveClass(/highlighted/, { timeout: 5_000 });
});
