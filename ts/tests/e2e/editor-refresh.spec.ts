// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Empty } from "@generated/anki/generic_pb";
import { AddNoteRequest, AddNoteResponse, Note, NoteId, UpdateNotesRequest } from "@generated/anki/notes_pb";
import { NotetypeId, NotetypeNames } from "@generated/anki/notetypes_pb";
import type { Page } from "@playwright/test";

import { expect, test } from "./fixtures";
import { callRpc, editableField, isRpc, isRpcResponse } from "./helpers";

async function addNote(page: Page, front: string): Promise<Note> {
    const notetypes = NotetypeNames.fromBinary(
        await callRpc(page, "getNotetypeNames", new Empty()),
    );
    const notetype = new NotetypeId({ ntid: notetypes.entries.find((entry) => entry.name === "Basic")!.id });
    const note = Note.fromBinary(await callRpc(page, "newNote", notetype));
    note.fields = [front, "abcd"];
    const added = AddNoteResponse.fromBinary(
        await callRpc(page, "addNote", new AddNoteRequest({ note, deckId: 1n })),
    );
    return Note.fromBinary(await callRpc(page, "getNote", new NoteId({ nid: added.noteId })));
}

async function loadNote(page: Page, note: Note): Promise<void> {
    await page.evaluate(
        ({ nid, notetypeId }) =>
            (window as any).loadNote({
                initial: true,
                nid,
                notetypeId,
                focusTo: 0,
            }),
        { nid: note.id.toString(), notetypeId: note.notetypeId.toString() },
    );
    await expect(editableField(page, 0)).toBeFocused();
    await page.evaluate(() => (window as any).saveNow());
}

async function openCurrentEditor(page: Page, note: Note): Promise<void> {
    await page.goto("/editor/?mode=current");
    await page.waitForFunction(() => typeof (window as any).loadNote === "function");
    await loadNote(page, note);
}

function deferred(): { promise: Promise<void>; resolve: () => void } {
    let resolve!: () => void;
    const promise = new Promise<void>((done) => resolve = done);
    return { promise, resolve };
}

async function notifyNoteChange(page: Page): Promise<void> {
    // Standalone Chromium does not receive the Qt webview's operation hook.
    // Deliver the same notification that Qt forwards after a backend save.
    await page.evaluate(() => (window as any).anki.onOperationDidExecute({ noteText: true }));
}

test("tabbing during a duplicate-status save keeps the next field and caret", async ({ editorPage: page }) => {
    const note = await addNote(page, "refresh original");
    await addNote(page, "refresh duplicate");
    await openCurrentEditor(page, note);
    const front = editableField(page, 0);
    const back = editableField(page, 1);
    const saved = deferred();
    const releaseSave = deferred();
    await page.route("**/_anki/updateNotes", async (route) => {
        const response = await route.fetch();
        saved.resolve();
        await releaseSave.promise;
        await route.fulfill({ response });
    });

    try {
        await front.press("ControlOrMeta+A");
        await front.pressSequentially("refresh duplicate");
        await front.press("Tab");
        await saved.promise;
        await expect(back).toBeFocused();
        await back.press("ArrowLeft");
        const reloads: string[] = [];
        page.on("request", (request) => {
            if (isRpc("getNotetype")(request)) {
                reloads.push(request.url());
            }
        });
        const reread = page.waitForResponse(isRpcResponse("getNote"));
        await notifyNoteChange(page);
        await reread;
        releaseSave.resolve();
        await expect(page.getByRole("link", { name: "Show Duplicates" })).toBeVisible();
        await page.waitForLoadState("networkidle");

        await expect(back).toBeFocused();
        await page.keyboard.type("!");
        await expect(back).toHaveText("abc!d");
        expect(reloads).toEqual([]);
    } finally {
        releaseSave.resolve();
    }
});

for (const changed of ["field", "tags"]) {
    test(`an external ${changed} update refreshes the editor`, async ({ editorPage: page }) => {
        const note = await addNote(page, `refresh external ${changed} original`);
        await openCurrentEditor(page, note);
        const front = editableField(page, 0);
        if (changed === "field") {
            note.fields[0] = "refresh external edit";
        } else {
            note.tags = ["external-tag"];
        }
        await callRpc(page, "updateNotes", new UpdateNotesRequest({ notes: [note] }));

        await notifyNoteChange(page);

        await expect(front).toHaveText(note.fields[0]);
        await expect(front).toBeFocused();
        if (changed === "tags") {
            await expect(page.getByText("external-tag", { exact: true })).toBeVisible();
        }
    });
}

test("a delayed operation notification does not reload a newly selected note", async ({ editorPage: page }) => {
    const previous = await addNote(page, "refresh previous note");
    const selected = await addNote(page, "refresh selected note");
    await openCurrentEditor(page, previous);
    previous.fields[0] = "refresh previous externally edited";
    await callRpc(page, "updateNotes", new UpdateNotesRequest({ notes: [previous] }));
    const readStarted = deferred();
    const releaseRead = deferred();
    let delayed = false;
    await page.route("**/_anki/getNote", async (route) => {
        const response = await route.fetch();
        if (!delayed) {
            delayed = true;
            readStarted.resolve();
            await releaseRead.promise;
        }
        await route.fulfill({ response });
    });

    try {
        await notifyNoteChange(page);
        await readStarted.promise;
        // Hold the new load before it replaces the current note object, but
        // after the response is received so network-idle remains observable.
        await page.evaluate(() => {
            const originalFetch = window.fetch.bind(window);
            let release!: () => void;
            const pendingMetadata = new Promise<void>((resolve) => release = resolve);
            (window as any).__releaseMetadata = release;
            let held = false;
            window.fetch = async (...args) => {
                const response = await originalFetch(...args);
                if (!held && String(args[0]).endsWith("/_anki/getNotetype")) {
                    held = true;
                    (window as any).__metadataReceived = true;
                    await pendingMetadata;
                }
                return response;
            };
        });
        const selectedLoaded = loadNote(page, selected);
        // Observe its rejection below if a stale refresh aborts the new load.
        void selectedLoaded.catch(() => undefined);
        await page.waitForFunction(() => (window as any).__metadataReceived);
        const reloads: string[] = [];
        page.on("request", (request) => {
            if (isRpc("getNotetype")(request)) {
                reloads.push(request.url());
            }
        });
        releaseRead.resolve();
        await page.waitForLoadState("networkidle");
        await page.evaluate(() => (window as any).__releaseMetadata());
        await selectedLoaded;

        await expect(editableField(page, 0)).toHaveText(selected.fields[0]);
        expect(reloads).toEqual([]);
    } finally {
        releaseRead.resolve();
        await page.evaluate(() => (window as any).__releaseMetadata?.());
    }
});
