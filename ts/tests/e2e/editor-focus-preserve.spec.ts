// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Cursor position across a note reload (issue #5892)
 *
 * Saving a note reports a `noteText` operation, and the editor answers it by
 * reloading the note (NoteEditor.svelte `onOperationDidExecute` → `reloadNote`).
 * `reloadNote()` used to replay `initialLoadArgs` verbatim, and that object
 * still held the `focusTo` value the note was opened with. `loadNoteInner()`
 * calls `focusField(focusTo)` at the end, so every save moved the cursor back
 * into the first field. The reload also blurs the field, which schedules the
 * next save, so the cursor kept jumping on its own with no user input at all.
 *
 * Assertions:
 *  - The fixture really loaded the persisted note, so the reload below cannot
 *    silently be a no-op (add mode never reloads on noteText).
 *  - The reload refocuses a field at all.
 *  - That field is the one the user was editing (1), not the one the note was
 *    opened with (0).
 *
 * Runs in "current" mode (the "Edit Current" dialog): `onOperationDidExecute`
 * only reloads on `noteText` when mode !== "add".
 */

import type { Page } from "@playwright/test";

import { expect, test } from "./fixtures";
import { bridgeCalls, editableField, fieldContainer } from "./helpers";

const FOCUS_CALL = "focus:";

/** data-index of the field that currently holds the caret, or -1 for none. */
function focusedFieldIndex(page: Page): Promise<number> {
    return page.evaluate(() => {
        // The caret sits in an <anki-editable> inside an open shadow root, so
        // document.activeElement is the .rich-text-editable host, which is
        // still inside .field-container in the light DOM.
        const container = document.activeElement?.closest(".field-container");
        return container ? Number(container.getAttribute("data-index")) : -1;
    });
}

function focusCallCount(calls: string[]): number {
    return calls.filter((call) => call.startsWith(FOCUS_CALL)).length;
}

test("a noteText reload keeps the cursor in the field being edited", async ({ currentEditor: page }) => {
    const front = editableField(page, 0);
    const back = editableField(page, 1);

    // The fixture loads a note whose fields are "front"/"back". Add mode would
    // start from an empty note and never reload, so this keeps the test honest.
    await expect(fieldContainer(page, 0).locator(".rich-text-editable")).toHaveText("front");
    await expect(fieldContainer(page, 1).locator(".rich-text-editable")).toHaveText("back");

    // Move the caret away from the field the note was opened with (focusTo: 0),
    // so that reusing the original focusTo is observably wrong.
    await front.click();
    await front.pressSequentially(" edited");
    await back.click();
    await back.pressSequentially(" edited");
    expect(await focusedFieldIndex(page)).toBe(1);

    // Every focusin reports itself over the bridge, including the ones the
    // reload triggers. onOperationDidExecute() is async and not awaited, so
    // without waiting for that report the assertions below race the reload.
    const focusCallsBefore = focusCallCount(await bridgeCalls(page));

    // Exactly what the backend does once the editor saves.
    await page.evaluate(() => (window as any).anki.onOperationDidExecute({ noteText: true }));

    await expect
        .poll(async () => focusCallCount(await bridgeCalls(page)), { timeout: 10_000 })
        .toBeGreaterThan(focusCallsBefore);

    // The reload must put the caret back where the user left it.
    await expect.poll(() => focusedFieldIndex(page), { timeout: 5_000 }).toBe(1);
});
