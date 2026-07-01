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

import { SetConfigJsonRequest } from "@generated/anki/config_pb";
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

/** Write an arbitrary value to a collection config key using the setConfigJson RPC. */
async function setConfigJson(page, key: string, value: unknown): Promise<void> {
    await callRpc(
        page,
        "setConfigJson",
        new SetConfigJsonRequest({
            key,
            valueJson: new TextEncoder().encode(JSON.stringify(value)),
        }),
    );
}

test.beforeEach(async ({ editor: page }) => {
    await ensureContextFixtures(page);
    // A fresh collection defaults to Mode A (addToCur = true per schema11).
    // Force Mode B so all tests share a known baseline; Mode A tests
    // re-enable it themselves and restore it in their finally block.
    await setConfigJson(page, "addToCur", false);
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

test("selected notetype and deck persist as context for the next note after add", async ({ editor: page }) => {
    // Criterion 1 (issue #4948): assert notetype and deck persist on reopen.
    // Using a non-default deck so the test exercises the real
    // _nt_{ntid}_lastDeck persistence path rather than the fallback to the
    // Default deck that would pass even with broken persistence logic.
    try {
        await openChooserAndSelect(page, "notetype", "Cloze");
        await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

        const newNotePromise = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        await fillAndAdd(page, "{{c1::notetype persist}}");
        await newNotePromise;

        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        // currentNotetypeId = Cloze; _nt_{cloze}_lastDeck = testDeckId.
        // Both must be restored through real persistence, not the Default fallback.
        await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
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

test("mode B: switching notetype auto-selects the last deck used with that notetype", async ({ editor: page }) => {
    // Adding.rs Mode B: each notetype remembers the last deck it was added to.
    // When the user switches notetype via the chooser, onNotetypeChange calls
    // defaultDeckForNotetype({ ntid }) and auto-selects the deck via
    // deckChooser.select — without the user touching the deck chooser at all.
    try {
        // Step 1: Establish _nt_{cloze}_lastDeck = testDeckId.
        await openChooserAndSelect(page, "notetype", "Cloze");
        await openChooserAndSelect(page, "deck", TEST_DECK_NAME);
        const newNote1 = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        await fillAndAdd(page, "{{c1::deck mapping}}");
        await newNote1;

        // Step 2: Reset to Basic + Default so the starting state is deterministic.
        await restoreBasicDefault(page);
        await reloadEditorAfterSetup(page);
        await expect(chooserButton(page, "notetype")).toHaveText("Basic");
        await expect(chooserButton(page, "deck")).toHaveText("Default");

        // Step 3: Switch to Cloze. NoteEditor reads _nt_{cloze}_lastDeck and
        // auto-calls deckChooser.select(testDeckId) without user interaction.
        await openChooserAndSelect(page, "notetype", "Cloze");

        // Step 4: Deck chooser must update on its own.
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME, {
            timeout: 8_000,
        });
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

test("mode A: last notetype used for the current deck is restored on reopen", async ({ editor: page }) => {
    // Adding.rs Mode A (AddingDefaultsToCurrentDeck = true): the deck is the
    // collection's current deck, and the notetype is the last one used with
    // that deck (_deck_{did}_lastNotetype).
    // Contrasts with Mode B where the notetype drives the deck selection.
    try {
        // Re-enable Mode A (beforeEach forced Mode B).
        await setConfigJson(page, "addToCur", true);

        // In Mode A, switching notetype does NOT auto-update the deck chooser
        // (the onNotetypeChange guard skips defaultDeckForNotetype when mode is A).
        await openChooserAndSelect(page, "notetype", "Cloze");
        await expect(chooserButton(page, "deck")).toHaveText("Default");

        // Add Cloze + Default → writes _deck_{Default}_lastNotetype = Cloze.
        const newNotePromise = page.waitForRequest(isRpc("newNote"), { timeout: 10_000 });
        await fillAndAdd(page, "{{c1::mode A test}}");
        await newNotePromise;

        // Simulate reopen.
        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        // Mode A: deck = current deck (Default), notetype = _deck_{Default}_lastNotetype = Cloze.
        await expect(chooserButton(page, "deck")).toHaveText("Default");
        await expect(chooserButton(page, "notetype")).toHaveText("Cloze");
    } finally {
        // Restore Mode B so the next test's beforeEach starts cleanly.
        await setConfigJson(page, "addToCur", false);
        await restoreBasicDefault(page);
    }
});

test("mode B: notetype with no lastDeck falls back to the collection's current deck", async ({ editor: page }) => {
    // adding.rs Mode B fallback path: when _nt_{ntid}_lastDeck is absent,
    // default_deck_for_notetype returns None and defaults_for_adding falls back to
    // get_current_deck_for_adding(), which returns the collection's current deck.
    // The test changes curDeck so that a broken fallback (e.g. always returning
    // Default) is detectable.
    try {
        // Point _nt_{basicNotetypeId}_lastDeck at a non-existent deck — this has
        // the same effect as the key being absent: get_deck() returns None and
        // default_deck_for_notetype returns None, triggering the curDeck fallback.
        await setConfigJson(page, `_nt_${basicNotetypeId}_lastDeck`, 999_999_999);
        // Set the collection's current deck (curDeck) to the non-Default testDeck.
        await setConfigJson(page, "curDeck", Number(testDeckId));

        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        // Deck must be testDeck (the current deck), not Default — confirming the
        // fallback path reads get_current_deck_for_adding() correctly.
        await expect(chooserButton(page, "notetype")).toHaveText("Basic");
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
    } finally {
        await setConfigJson(page, "curDeck", 1);
        await restoreBasicDefault(page);
    }
});

test("mode B: deleted lastDeck is ignored and current deck is used instead", async ({ editor: page }) => {
    // adding.rs: default_deck_for_notetype calls get_deck(last_deck_id) and skips
    // if None (deck was deleted). The fallback must produce curDeck, not Default.
    try {
        await setConfigJson(page, `_nt_${basicNotetypeId}_lastDeck`, 999_999_999);
        await setConfigJson(page, "curDeck", Number(testDeckId));

        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        await expect(chooserButton(page, "notetype")).toHaveText("Basic");
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
    } finally {
        await setConfigJson(page, "curDeck", 1);
        await restoreBasicDefault(page);
    }
});

test("mode A: deck with no lastNotetype falls back to the global current notetype", async ({ editor: page }) => {
    // adding.rs Mode A fallback: when _deck_{did}_lastNotetype is absent,
    // default_notetype_for_deck falls back to get_current_notetype_for_adding(),
    // which returns the global current notetype. restoreBasicDefault() (run by
    // previous tests and in finally blocks) leaves that global notetype as Basic.
    try {
        await setConfigJson(page, "addToCur", true);
        // NotetypeId 0 does not exist → get_notetype(0) returns None → fallback.
        await setConfigJson(page, `_deck_${testDeckId}_lastNotetype`, 0);
        await setConfigJson(page, "curDeck", Number(testDeckId));

        const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
            timeout: 10_000,
        });
        await page.evaluate(() => (window as any).loadNote({ initial: true }));
        await defaultsReqPromise;

        // Mode A: deck = testDeck (curDeck), notetype = Basic (global current notetype fallback).
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
        await expect(chooserButton(page, "notetype")).toHaveText("Basic");
    } finally {
        await setConfigJson(page, "addToCur", false);
        await setConfigJson(page, "curDeck", 1);
        await restoreBasicDefault(page);
    }
});
