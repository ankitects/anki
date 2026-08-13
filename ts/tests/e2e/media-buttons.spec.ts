// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Editor toolbar media buttons (#5330)
 *
 * Covers TemplateButtons.svelte, which branches on isLegacy:
 *
 * Non-legacy (the SvelteKit editor):
 *  - Attach: openFilePicker RPC → addMediaFromPath RPC → inserthtml
 *  - Record: recordAudio RPC → addMediaFromPath RPC → inserthtml → playFile
 *    RPC for immediate playback.
 *
 * Legacy (Qt drives the page, isLegacy=true):
 *  - Both buttons must go through the legacy Qt routines instead of the
 *    RPCs: bridgeCommand("attach") / bridgeCommand("record"), then wait for
 *    Python to add the file and call
 *    require("anki/TemplateButtons").resolveMedia(html)
 *    (editor_legacy.py:880-891). The HTML is inserted when the field regains
 *    focus after the native dialog closes (TemplateButtons.svelte:58-64).
 *
 * The legacy tests replicate the Qt side of the round trip: blur the field
 * (the native dialog takes focus), eval resolveMedia(), refocus the field.
 */

import { openFilePickerRequest } from "@generated/anki/frontend_pb";
import { String as GenericString } from "@generated/anki/generic_pb";
import type { Page } from "@playwright/test";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

import { expect, test } from "./fixtures";
import { bridgeCalls, decodeRequestBody, editableField, isRpc } from "./helpers";

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "anki-e2e-media-"));
test.afterAll(() => fs.rmSync(tmpDir, { recursive: true, force: true }));

// 1x1 transparent PNG; addMediaFromPath copies the bytes verbatim.
const PNG_BYTES = Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
    "base64",
);
// Empty canonical RIFF/WAVE file.
const WAV_BYTES = Buffer.from(
    "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=",
    "base64",
);

function protoStringBody(val: string): Buffer {
    return Buffer.from(new GenericString({ val }).toBinary());
}

function attachButton(scope: Page | ReturnType<Page["locator"]>): ReturnType<Page["locator"]> {
    return scope.locator("button[title^=\"Attach pictures\"]");
}

function recordButton(scope: Page | ReturnType<Page["locator"]>): ReturnType<Page["locator"]> {
    return scope.locator("button[title^=\"Record audio\"]");
}

test("attach button uses openFilePicker+addMediaFromPath RPCs, not the Qt bridge", async ({ editor: page }) => {
    const imgName = `e2e-attach-${Date.now()}.png`;
    const imgPath = path.join(tmpDir, imgName);
    fs.writeFileSync(imgPath, PNG_BYTES);

    // The real RPC would open a native Qt dialog and hang the headless
    // instance; answer it with the temp file instead. addMediaFromPath then
    // runs against the real backend.
    await page.route("**/_anki/openFilePicker", (route) =>
        route.fulfill({
            contentType: "application/binary",
            body: protoStringBody(imgPath),
        }));

    const field = editableField(page, 0);
    await field.click();

    const pickerReqPromise = page.waitForRequest(isRpc("openFilePicker"), { timeout: 10_000 });
    const addMediaRespPromise = page.waitForResponse(
        (resp) => isRpc("addMediaFromPath")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );

    await attachButton(page).click();

    const pickerReq = await pickerReqPromise;
    const decoded = decodeRequestBody(pickerReq, openFilePickerRequest);
    expect(decoded.extensions).toContain("png");
    expect(decoded.extensions).toContain("wav");

    // The backend must have copied the file and the editor inserted a link.
    // The name is unique, so the media folder keeps it as-is.
    await addMediaRespPromise;
    await expect(field.locator(`img[src$="${imgName}"]`)).toBeAttached({
        timeout: 5_000,
    });

    // The legacy Qt routine must NOT have been used.
    const calls = await bridgeCalls(page);
    expect(calls).not.toContain("attach");
});

test("record button uses recordAudio+addMediaFromPath RPCs and plays the file", async ({ editor: page }) => {
    const wavName = `e2e-record-${Date.now()}.wav`;
    const wavPath = path.join(tmpDir, wavName);
    fs.writeFileSync(wavPath, WAV_BYTES);

    // recordAudio would pop Qt's recorder; answer with a prepared file.
    await page.route("**/_anki/recordAudio", (route) =>
        route.fulfill({
            contentType: "application/binary",
            body: protoStringBody(wavPath),
        }));
    // playFile would ask Qt to actually play audio; stub it out.
    await page.route("**/_anki/playFile", (route) =>
        route.fulfill({
            contentType: "application/binary",
            body: Buffer.from(""),
        }));

    const field = editableField(page, 0);
    await field.click();

    const recordReqPromise = page.waitForRequest(isRpc("recordAudio"), { timeout: 10_000 });
    const addMediaRespPromise = page.waitForResponse(
        (resp) => isRpc("addMediaFromPath")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );
    const playFileReqPromise = page.waitForRequest(isRpc("playFile"), { timeout: 10_000 });

    await recordButton(page).click();

    await recordReqPromise;
    await addMediaRespPromise;

    // The recording is inserted as a sound tag and played immediately.
    await expect(field).toContainText(`[sound:${wavName}]`, { timeout: 5_000 });
    const playFileReq = await playFileReqPromise;
    expect(decodeRequestBody(playFileReq, GenericString).val).toBe(wavName);

    const calls = await bridgeCalls(page);
    expect(calls).not.toContain("record");
});

/**
 * Drives the legacy branch of a media button and replicates Qt's half of the
 * round trip. Returns the list of media RPC methods the page issued — the
 * legacy path must not issue any.
 */
async function legacyMediaRoundTrip(
    page: Page,
    opts: { button: ReturnType<Page["locator"]>; command: string; html: string },
): Promise<string[]> {
    const rpcHits: string[] = [];
    page.on("request", (req) => {
        for (const method of ["openFilePicker", "recordAudio", "addMediaFromPath", "playFile"]) {
            if (isRpc(method)(req)) {
                rpcHits.push(method);
            }
        }
    });

    const field = editableField(page, 0);
    await field.click();
    await opts.button.click();

    // The click must delegate to the Qt bridge.
    expect(await bridgeCalls(page)).toContain(opts.command);

    // Replicate Qt (editor_legacy.py): the native dialog takes focus
    // from the field, Python adds the file and evals resolveMedia() with the
    // resulting HTML, and the field regains focus when the dialog closes —
    // which is when the pending insert runs.
    await field.evaluate((el) => (el as HTMLElement).blur());
    await page.evaluate(
        (html) => (window as any).require("anki/TemplateButtons").resolveMedia(html),
        opts.html,
    );
    await field.evaluate((el) => (el as HTMLElement).focus());

    return rpcHits;
}

test("legacy attach button delegates to the Qt bridge and inserts resolved media on refocus", async ({ legacyEditor: page }) => {
    const legacyRoot = page.locator(".note-editor").nth(1);
    const rpcHits = await legacyMediaRoundTrip(page, {
        button: attachButton(legacyRoot),
        command: "attach",
        html: "<img src=\"e2e-legacy.jpg\">",
    });

    await expect(editableField(page, 0).locator("img[src$=\"e2e-legacy.jpg\"]")).toBeAttached({
        timeout: 5_000,
    });
    // Adding the media is Python's job in legacy mode; no RPC may fire.
    expect(rpcHits).toEqual([]);
});

test("legacy record button delegates to the Qt bridge and inserts resolved media on refocus", async ({ legacyEditor: page }) => {
    const legacyRoot = page.locator(".note-editor").nth(1);
    const rpcHits = await legacyMediaRoundTrip(page, {
        button: recordButton(legacyRoot),
        command: "record",
        html: "[sound:e2e-legacy-rec.mp3]",
    });

    await expect(editableField(page, 0)).toContainText("[sound:e2e-legacy-rec.mp3]", {
        timeout: 5_000,
    });
    expect(rpcHits).toEqual([]);
});
