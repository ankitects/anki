// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { type Page, test as base } from "@playwright/test";

import { Empty } from "@generated/anki/generic_pb";
import { AddNoteRequest, AddNoteResponse, Note } from "@generated/anki/notes_pb";
import { NotetypeNames } from "@generated/anki/notetypes_pb";

import { callRpc } from "./helpers";

export { expect } from "@playwright/test";

interface AnkiFixtures {
    /** Page navigated to /editor/?mode=add with bridgeCommand stubbed. */
    editorPage: Page;
    /**
     * editorPage after loadNote({ initial: true }) has resolved and the first
     * field container is visible. Suitable for all editor interaction tests.
     */
    editor: Page;
    /**
     * editorPage with a second NoteEditor mounted via setupEditor("add", true)
     * — the way the legacy Qt editor loads it (editor_legacy.py) — and
     * seeded with two fields the way Python drives the webview (editor_legacy.py).
     */
    legacyEditor: Page;
    /**
     * Page navigated to /editor/?mode=current — the "Edit Current" flow, which
     * is what EditorMode calls edit mode — with bridgeCommand stubbed and an
     * existing note loaded, so that the non-add code paths are exercised.
     *
     * Needed because NoteEditor.svelte only reloads the note on a noteText
     * change when mode !== "add" (onOperationDidExecute), and reloadNote() can
     * only replay a load that happened with initial: true.
     *
     * Creates one note in the collection on every test that uses it.
     */
    currentEditor: Page;
}

async function installBridgeStub(page: Page): Promise<void> {
    // Runs before any page script; intercepts window.bridgeCommand so the
    // editor doesn't throw when Qt's webChannelTransport is unavailable, and
    // records every call for assertion.
    await page.addInitScript(() => {
        (window as any).__bridgeCalls = [];
        (window as any).bridgeCommand = (
            cmd: string,
            _callback?: (value: unknown) => void,
        ): void => {
            (window as any).__bridgeCalls.push(cmd);
        };
    });
}

/**
 * Persists a Basic note through the backend and returns the ids loadNote needs,
 * so that current mode has something to load.
 *
 * Ids go over as strings: loadNoteInner() coerces them with BigInt().
 */
async function createNoteForEditing(
    page: Page,
): Promise<{ nid: string; notetypeId: string }> {
    const notetypes = NotetypeNames.fromBinary(
        await callRpc(page, "getNotetypeNames", new Empty()),
    );
    const basic = notetypes.entries.find((entry) => entry.name === "Basic");
    if (!basic) {
        throw new Error("Expected a stock Basic notetype in the e2e profile");
    }
    const response = AddNoteResponse.fromBinary(
        await callRpc(
            page,
            "addNote",
            new AddNoteRequest({
                note: new Note({
                    notetypeId: basic.id,
                    fields: ["front", "back"],
                    tags: [],
                }),
                // The Default deck always exists in a fresh profile.
                deckId: 1n,
            }),
        ),
    );
    return { nid: response.noteId.toString(), notetypeId: basic.id.toString() };
}

export const test = base.extend<AnkiFixtures>({
    editorPage: async ({ page }, use) => {
        await installBridgeStub(page);
        await page.goto("/editor/?mode=add", { waitUntil: "domcontentloaded" });
        await page.waitForSelector(".note-editor", { timeout: 15_000 });
        await use(page);
    },

    editor: async ({ editorPage }, use) => {
        // NoteEditor.svelte exposes loadNote via Object.assign(globalThis, ...)
        // inside onMount. Wait for it before calling.
        await editorPage.waitForFunction(
            () => typeof (window as any).loadNote === "function",
            { timeout: 15_000 },
        );
        // initial: true triggers defaultsForAdding() so the deck/notetype
        // choosers are populated from the backend.
        await editorPage.evaluate(() => (window as any).loadNote({ initial: true }));
        // At least one field container signals that the note loaded.
        await editorPage.waitForSelector(".field-container", { timeout: 15_000 });
        await use(editorPage);
    },

    legacyEditor: async ({ editorPage }, use) => {
        // The page auto-mounts a non-legacy editor; mounting a second one in
        // legacy mode overwrites the globals (setFields, loadNote,
        // require("anki/TemplateButtons"), ...) exactly like the sole
        // instance does under Qt. Snapshot setFields first so we can detect
        // when the legacy instance's onMount has replaced it.
        await editorPage.evaluate(() => {
            (window as any).__preLegacySetFields = (window as any).setFields;
            return (window as any).setupEditor("add", true);
        });
        await editorPage.waitForFunction(
            () =>
                document.querySelectorAll(".note-editor").length === 2
                && (window as any).setFields !== (window as any).__preLegacySetFields,
            { timeout: 15_000 },
        );
        // In legacy mode Python pushes note state through evaluated JS
        // instead of loadNote; replicate the same calls, in the same order.
        await editorPage.evaluate(() => {
            const w = window as any;
            w.setFields(["Front", "Back"], ["", ""]);
            w.setIsImageOcclusion(false);
            w.setNotetypeMeta({ id: 0, modTime: 0 });
            w.setCollapsed([false, false]);
            w.setClozeFields([false, false]);
            w.setPlainTexts([false, false]);
            w.setDescriptions(["", ""]);
            w.setFonts([["Arial", 20, false], ["Arial", 20, false]]);
            w.setNoteId(0);
            w.setColorButtons(["#00f", "#00f"]);
            w.setTags([]);
            w.setTagsCollapsed(false);
            w.setMathjaxEnabled(true);
            w.setShrinkImages(true);
            w.setCloseHTMLTags(true);
            w.setSticky([false, false]);
            w.triggerChanges();
        });
        await editorPage.waitForSelector(".field-container", { timeout: 15_000 });
        await use(editorPage);
    },

    currentEditor: async ({ page }, use) => {
        await installBridgeStub(page);
        await page.goto("/editor/?mode=current", { waitUntil: "domcontentloaded" });
        await page.waitForSelector(".note-editor", { timeout: 15_000 });
        await page.waitForFunction(
            () => typeof (window as any).loadNote === "function",
            { timeout: 15_000 },
        );
        const { nid, notetypeId } = await createNoteForEditing(page);
        // Mirrors what qt/aqt/editor.py load_note() sends for Edit Current:
        // nid + notetypeId + focusTo (editcurrent.py passes focusTo=0), with
        // initial: true so that initialLoadArgs is recorded. reloadNote()
        // replays that object, so the focusTo it carries matters.
        await page.evaluate(
            (args) =>
                (window as any).loadNote({
                    nid: args.nid,
                    notetypeId: args.notetypeId,
                    focusTo: 0,
                    initial: true,
                }),
            { nid, notetypeId },
        );
        await page.waitForSelector(".field-container", { timeout: 15_000 });
        await use(page);
    },
});
