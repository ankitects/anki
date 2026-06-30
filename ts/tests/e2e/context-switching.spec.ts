// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Context switching coverage for issue #4948.
 *
 * These tests exercise the Svelte editor's add-mode deck/notetype choosers
 * end-to-end: chooser UI, reload-on-notetype-change, addNote payloads, and the
 * backend default-context persistence that is written only after a successful
 * add.
 */

import { DeckNames, GetDeckNamesRequest } from "@generated/anki/decks_pb";
import { Empty, String as GenericString } from "@generated/anki/generic_pb";
import { AddNoteRequest } from "@generated/anki/notes_pb";
import { NotetypeNames } from "@generated/anki/notetypes_pb";

import { expect, test } from "./fixtures";
import {
    callRpc,
    chooserButton,
    decodeRequestBody,
    editableField,
    fieldContainer,
    isRpc,
    isRpcResponse,
    openChooserAndSelect,
} from "./helpers";

const TEST_DECK_NAME = `Context Switching ${Date.now()}`;
const DEFAULT_DECK_ID = 1n;

let testDeckId: bigint | null = null;
let basicNotetypeId: bigint | null = null;
let clozeNotetypeId: bigint | null = null;

async function decodedRpc<T>(
    page,
    method: string,
    message,
    responseType: { fromBinary(bytes: Uint8Array): T },
    opChangesType = 0,
): Promise<T> {
    return responseType.fromBinary(await callRpc(page, method, message, opChangesType));
}

async function ensureContextFixtures(page): Promise<void> {
    if (testDeckId !== null && basicNotetypeId !== null && clozeNotetypeId !== null) {
        return;
    }

    const notetypeNames = await decodedRpc(
        page,
        "getNotetypeNames",
        new Empty(),
        NotetypeNames,
    );
    basicNotetypeId = notetypeNames.entries.find((entry) => entry.name === "Basic")
        ?.id ?? null;
    clozeNotetypeId = notetypeNames.entries.find((entry) => entry.name === "Cloze")
        ?.id ?? null;

    if (basicNotetypeId === null || clozeNotetypeId === null) {
        throw new Error("Expected stock Basic and Cloze notetypes in e2e profile");
    }

    await callRpc(
        page,
        "importJsonString",
        new GenericString({
            val: JSON.stringify({
                default_deck: TEST_DECK_NAME,
                notes: [],
            }),
        }),
        2,
    );

    const deckNames = await decodedRpc(
        page,
        "getDeckNames",
        new GetDeckNamesRequest(),
        DeckNames,
    );
    testDeckId = deckNames.entries.find((entry) => entry.name === TEST_DECK_NAME)?.id
        ?? null;
    if (testDeckId === null) {
        throw new Error(`Expected imported test deck "${TEST_DECK_NAME}"`);
    }
}

async function loadEditorInitial(page): Promise<void> {
    await page.waitForFunction(
        () => typeof (window as any).loadNote === "function",
        { timeout: 15_000 },
    );
    await page.evaluate(() => (window as any).loadNote({ initial: true }));
    await page.waitForSelector(".field-container", { timeout: 15_000 });
}

async function reloadEditorAfterSetup(page): Promise<void> {
    await page.reload({ waitUntil: "domcontentloaded" });
    await page.waitForSelector(".note-editor", { timeout: 15_000 });
    await loadEditorInitial(page);
}

async function loadSpecificContext(
    page,
    notetypeId: bigint,
    deckId: bigint,
): Promise<void> {
    await page.evaluate(
        ({ notetypeId, deckId }) =>
            (window as any).loadNote({
                notetypeId: BigInt(notetypeId),
                deckId: BigInt(deckId),
            }),
        { notetypeId: notetypeId.toString(), deckId: deckId.toString() },
    );
    await page.waitForSelector(".field-container", { timeout: 15_000 });
}

async function fillAndAdd(page, fieldText: string): Promise<AddNoteRequest> {
    const field = editableField(page, 0);
    await field.click();
    await field.pressSequentially(fieldText);

    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), {
        timeout: 10_000,
    });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    const addNoteReq = await addNoteReqPromise;
    await page.waitForResponse(
        (resp) => isRpcResponse("addNote")(resp) && resp.status() < 400,
        { timeout: 10_000 },
    );

    return decodeRequestBody(addNoteReq, AddNoteRequest);
}

async function restoreBasicDefault(page): Promise<void> {
    await loadSpecificContext(page, basicNotetypeId!, DEFAULT_DECK_ID);
    await fillAndAdd(page, "Restore default context");
}

test.beforeEach(async ({ editor: page }) => {
    await ensureContextFixtures(page);
    await reloadEditorAfterSetup(page);
});

test("switching notetype updates the editor fields through the Svelte path", async ({ editor: page }) => {
    const getNotetypeReqPromise = page.waitForRequest(isRpc("getNotetype"), {
        timeout: 10_000,
    });
    const newNoteReqPromise = page.waitForRequest(isRpc("newNote"), {
        timeout: 10_000,
    });

    await openChooserAndSelect(page, "notetype", "Cloze");

    await getNotetypeReqPromise;
    await newNoteReqPromise;

    await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
    await expect(fieldContainer(page, 0).getByText("Text", { exact: true }))
        .toBeVisible();
    await expect(
        fieldContainer(page, 1).getByText("Back Extra", { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Front", exact: true }))
        .toHaveCount(0);
    await expect(page.getByRole("button", { name: "Back", exact: true }))
        .toHaveCount(0);
});

test("selected notetype persists as context for the next note after add", async ({ editor: page }) => {
    // Criterion 1 (issue #4948): assert notetype persists on the next opened note.
    // Tested in isolation so any regression in notetype-only persistence is
    // visible independently of the combined notetype+deck scenario below.
    try {
        await openChooserAndSelect(page, "notetype", "Cloze");

        // Set up newNote listener before the add so we don't race against the
        // post-add reload that fires it.
        const newNotePromise = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        await fillAndAdd(page, "{{c1::notetype persist}}");
        await newNotePromise;

        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
    } finally {
        await restoreBasicDefault(page);
    }
});

test("switching deck sends addNote to the selected deck", async ({ editor: page }) => {
    await openChooserAndSelect(page, "deck", TEST_DECK_NAME);
    await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);

    const decoded = await fillAndAdd(page, "Deck switch payload");

    expect(decoded.deckId).toBe(testDeckId);
    expect(decoded.note?.notetypeId).toBe(basicNotetypeId);
});

test("switching deck and notetype sends addNote with both selected ids", async ({ editor: page }) => {
    // Criterion 3 (issue #4948): both choices persist correctly in the same session.
    // "Same session" means the choosers still reflect the selection immediately
    // after the add — before any explicit reopen.
    try {
        await openChooserAndSelect(page, "notetype", "Cloze");
        await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

        const newNotePromise = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        const decoded = await fillAndAdd(page, "{{c1::context answer}}");

        // Payload must carry both selected ids.
        expect(decoded.deckId).toBe(testDeckId);
        expect(decoded.note?.notetypeId).toBe(clozeNotetypeId);

        // Wait for the post-add reload to settle before checking chooser state.
        await newNotePromise;

        // Within-session context: both choosers must still reflect the selection
        // immediately after the add (before any reopen).
        await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
    } finally {
        await restoreBasicDefault(page);
    }
});

test("reopening add mode remembers the last added deck and notetype context", async ({ editor: page }) => {
    // Criterion 4 (issue #4948): assert the last used notetype and deck are
    // pre-selected when the editor is reopened.
    try {
        await openChooserAndSelect(page, "notetype", "Cloze");
        await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

        const newNotePromise = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        await fillAndAdd(page, "{{c1::remembered context}}");
        await newNotePromise;

        // Intermediate check: context is intact immediately after the add,
        // before any explicit reopen.
        await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);

        // Reopen check: simulate re-entering Add mode (e.g. closing and
        // reopening the Add Cards window).
        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        // Both must be remembered after reopen (FIX REQUIRED in PR #4029).
        // Using soft assertions so both chooser states are reported even when
        // the first one fails — the combined notetype+deck reopen scenario is
        // known to not restore context correctly.
        await expect.soft(chooserButton(page, "notetype")).toHaveText("Cloze");
        await expect.soft(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
    } finally {
        await restoreBasicDefault(page);
    }
});
