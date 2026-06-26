// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Suite 1 – note-add roundtrip (issue #4930)
 *
 * Verifies that the new TypeScript editor (PR #4029) sends a well-formed
 * addNote RPC payload when the user types into fields and clicks Add.
 *
 * Assertions:
 *  - /_anki/addNote request body decodes to AddNoteRequest with correct field
 *    values and a non-zero deckId.
 *  - /_anki/updateNotes is NOT fired (add-mode contract: updating existing
 *    notes must never happen during an add flow).
 *  - /_anki/newNote fires after Add (signals the form was reset).
 *  - First field clears after Add.
 *  - window.__bridgeCalls contains "saved" (NoteEditor.svelte:438).
 *
 * This test mutates the collection — a note is persisted on every run.
 */

import { AddNoteRequest } from "@generated/anki/notes_pb";

import { expect, test } from "./fixtures";
import { bridgeCalls, decodeRequestBody, editableField, rpcUrl } from "./helpers";

test("typing into fields and clicking Add sends correct addNote payload", async ({
    editor: page,
}) => {
    const field0 = editableField(page, 0);
    const field1 = editableField(page, 1);

    // pressSequentially() fires real keydown/keypress/keyup events, which are
    // necessary for the Svelte content store to detect changes in the shadow-DOM
    // contenteditable (fill() only sets textContent and may miss the debounce).
    await field0.click();
    await field0.pressSequentially("Hello World");

    await field1.click();
    await field1.pressSequentially("Goodbye World");

    // Track whether the forbidden updateNotes RPC fires at any point.
    let updateNotesFired = false;
    page.on("request", (req) => {
        if (req.url().includes(rpcUrl("updateNotes"))) {
            updateNotesFired = true;
        }
    });

    // Set up addNote capture BEFORE clicking Add.
    // waitForRequest resolves on the next matching request, so it is safe to
    // set it up here without racing against earlier background RPCs.
    const addNoteReqPromise = page.waitForRequest(
        (req) => req.url().includes(rpcUrl("addNote")),
        { timeout: 10_000 },
    );

    // exact: true avoids matching the "Add tag" button in the tag editor.
    await page.getByRole("button", { name: "Add", exact: true }).click();

    const addNoteReq = await addNoteReqPromise;
    const decoded = decodeRequestBody(addNoteReq, AddNoteRequest);

    expect(decoded.note?.fields[0]).toBe("Hello World");
    expect(decoded.note?.fields[1]).toBe("Goodbye World");
    expect(decoded.deckId).not.toBe(0n);

    // Response must be successful.
    await page.waitForResponse(
        (resp) =>
            resp.url().includes(rpcUrl("addNote")) && resp.status() < 400,
        { timeout: 10_000 },
    );

    // After a successful add, the editor calls loadNote({ stickyFieldsFrom:
    // note }) which in turn calls newNote. This is the reliable "form was
    // reset" signal (the 500 ms toast is too short to assert reliably).
    await page.waitForRequest(
        (req) => req.url().includes(rpcUrl("newNote")),
        { timeout: 10_000 },
    );

    // Field 0 must clear after the add.
    await expect(field0).toHaveText("", { timeout: 5_000 });

    // NoteEditor.svelte:578-579 calls saveNow() before addNote; saveNow sends
    // bridgeCommand("saved") when !isLegacy.
    const calls = await bridgeCalls(page);
    expect(calls).toContain("saved");

    // updateNotes must never fire during an add flow.
    expect(updateNotesFired).toBe(false);
});
