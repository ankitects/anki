// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Context switching coverage — grouped by concern:
 *   1. Chooser UI and session behaviour — live chooser interactions within a
 *      single add session.
 *   2. addNote payload — the IDs carried to the backend match the chooser state.
 *   3. Mode B (default): each notetype remembers its own last-used deck; the
 *      notetype drives deck selection both live (session) and on reopen.
 *   4. Mode A (AddingDefaultsToCurrentDeck=true): the current deck is fixed
 *      and each deck remembers the last notetype used with it.
 *
 * See rslib/src/adding.rs for the backend logic exercised here.
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
    basicNotetypeId = notetypeNames.entries.find((entry) => entry.name === "Basic")?.id ?? null;
    clozeNotetypeId = notetypeNames.entries.find((entry) => entry.name === "Cloze")?.id ?? null;

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
    testDeckId = deckNames.entries.find((entry) => entry.name === TEST_DECK_NAME)?.id ?? null;
    if (testDeckId === null) {
        throw new Error(`Expected imported test deck "${TEST_DECK_NAME}"`);
    }
}

async function loadEditorInitial(page): Promise<void> {
    await page.waitForFunction(() => typeof (window as any).loadNote === "function", {
        timeout: 15_000,
    });
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

// ─── 1. Chooser UI and session behaviour ─────────────────────────────────────

test.describe("chooser UI and session behaviour", () => {
    test("notetype chooser updates field list via the Svelte path", async ({ editor: page }) => {
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
        await expect(
            fieldContainer(page, 0).getByText("Text", { exact: true }),
        ).toBeVisible();
        await expect(
            fieldContainer(page, 1).getByText("Back Extra", { exact: true }),
        ).toBeVisible();
        await expect(
            page.getByRole("button", { name: "Front", exact: true }),
        ).toHaveCount(0);
        await expect(
            page.getByRole("button", { name: "Back", exact: true }),
        ).toHaveCount(0);
    });
});

// ─── 2. addNote payload ───────────────────────────────────────────────────────

test.describe("addNote payload", () => {
    test("deck chooser selection is reflected in the addNote payload", async ({ editor: page }) => {
        await openChooserAndSelect(page, "deck", TEST_DECK_NAME);
        await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);

        const decoded = await fillAndAdd(page, "Deck switch payload");

        expect(decoded.deckId).toBe(testDeckId);
        expect(decoded.note?.notetypeId).toBe(basicNotetypeId);
    });

    test("notetype and deck chooser selections both appear in the addNote payload", async ({ editor: page }) => {
        // "Same session" means the choosers still reflect the selection immediately
        // after the add — before any explicit reopen.
        try {
            await openChooserAndSelect(page, "notetype", "Cloze");
            await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

            const newNotePromise = page.waitForRequest(isRpc("newNote"), {
                timeout: 10_000,
            });
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
});

// ─── 3. Mode B: context behaviour (default) ──────────────────────────────────

test.describe("mode B: context behaviour (default)", () => {
    test("notetype switch auto-selects the last deck used with that notetype", async ({ editor: page }) => {
        // adding.rs Mode B: each notetype remembers the last deck it was added to.
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

    test("notetype and deck context persists after add and across reopen", async ({ editor: page }) => {
        // Using a non-default deck exercises the real _nt_{ntid}_lastDeck persistence
        // path rather than the Default fallback that would pass even with broken logic.
        try {
            await openChooserAndSelect(page, "notetype", "Cloze");
            await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

            const newNotePromise = page.waitForRequest(isRpc("newNote"), {
                timeout: 10_000,
            });
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

    test("notetype and deck remain selected within session and after explicit reopen", async ({ editor: page }) => {
        try {
            await openChooserAndSelect(page, "notetype", "Cloze");
            await openChooserAndSelect(page, "deck", TEST_DECK_NAME);

            const newNotePromise = page.waitForRequest(isRpc("newNote"), {
                timeout: 10_000,
            });
            await fillAndAdd(page, "{{c1::remembered context}}");
            await newNotePromise;

            // Within-session check: context must be intact immediately after the add,
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

            // Using soft assertions so both chooser states are reported even if one
            // fails — the combined notetype+deck reopen scenario is the primary
            // indicator of the persistence bug documented in PR #4029.
            await expect.soft(chooserButton(page, "notetype")).toHaveText("Cloze");
            await expect.soft(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
        } finally {
            await restoreBasicDefault(page);
        }
    });

    test("missing lastDeck history falls back to the current deck on reopen", async ({ editor: page }) => {
        // adding.rs: when _nt_{ntid}_lastDeck is absent, default_deck_for_notetype
        // returns None and defaults_for_adding falls back to get_current_deck_for_adding().
        // curDeck is set to testDeck so a broken fallback (e.g. always Default) is detectable.
        try {
            // Point _nt_{basicNotetypeId}_lastDeck at a non-existent deck — same effect
            // as the key being absent: get_deck() returns None, triggering the curDeck fallback.
            await setConfigJson(page, `_nt_${basicNotetypeId}_lastDeck`, 999_999_999);
            await setConfigJson(page, "curDeck", Number(testDeckId));

            const defaultsReqPromise = page.waitForRequest(isRpc("defaultsForAdding"), {
                timeout: 10_000,
            });
            await page.evaluate(() => (window as any).loadNote({ initial: true }));
            await defaultsReqPromise;

            // Deck must be testDeck (the current deck), not Default.
            await expect(chooserButton(page, "notetype")).toHaveText("Basic");
            await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
        } finally {
            await setConfigJson(page, "curDeck", 1);
            await restoreBasicDefault(page);
        }
    });

    test("stale lastDeck reference falls back to the current deck on reopen", async ({ editor: page }) => {
        // adding.rs: default_deck_for_notetype calls get_deck(last_deck_id) and returns
        // None when the deck no longer exists. The fallback must produce curDeck, not Default.
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
});

// ─── 4. Mode A: reopen context (deck-centric) ────────────────────────────────

test.describe("mode A: reopen context (deck-centric)", () => {
    test("last notetype for the current deck is restored on reopen", async ({ editor: page }) => {
        // adding.rs Mode A (AddingDefaultsToCurrentDeck = true): deck = collection's
        // current deck, notetype = last notetype used with that deck (_deck_{did}_lastNotetype).
        // Contrasts with Mode B where the notetype drives the deck selection.
        try {
            // Re-enable Mode A (beforeEach forced Mode B).
            await setConfigJson(page, "addToCur", true);

            // In Mode A, switching notetype does NOT auto-update the deck chooser
            // (the onNotetypeChange guard skips defaultDeckForNotetype when mode is A).
            await openChooserAndSelect(page, "notetype", "Cloze");
            await expect(chooserButton(page, "deck")).toHaveText("Default");

            // Add Cloze + Default → writes _deck_{Default}_lastNotetype = Cloze.
            const newNotePromise = page.waitForRequest(isRpc("newNote"), {
                timeout: 10_000,
            });
            await fillAndAdd(page, "{{c1::mode A test}}");
            await newNotePromise;

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

    test("missing deck–notetype history falls back to the global notetype on reopen", async ({ editor: page }) => {
        // adding.rs Mode A fallback: when _deck_{did}_lastNotetype is absent,
        // default_notetype_for_deck falls back to get_current_notetype_for_adding().
        // restoreBasicDefault() leaves the global notetype as Basic.
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

            // Mode A: deck = testDeck (curDeck), notetype = Basic (global fallback).
            await expect(chooserButton(page, "deck")).toHaveText(TEST_DECK_NAME);
            await expect(chooserButton(page, "notetype")).toHaveText("Basic");
        } finally {
            await setConfigJson(page, "addToCur", false);
            await setConfigJson(page, "curDeck", 1);
            await restoreBasicDefault(page);
        }
    });
});
