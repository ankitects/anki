// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Editor toolbar media buttons (#5330)
 *
 * Covers the legacy and new paths in TemplateButtons.svelte
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
    fs.writeFileSync(imgPath, Buffer.from(""));

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

    await addMediaRespPromise;
    await expect(field.locator(`img[src$="${imgName}"]`)).toBeAttached({
        timeout: 5_000,
    });

    // The legacy Qt routine must NOT have been used.
    const calls = await bridgeCalls(page);
    expect(calls).not.toContain("attach");
});

for (const source of ["attachment", "recording"] as const) {
    test(`audio ${source} inserts the file and starts playback exactly once`, async ({ editor: page }) => {
        const wavName = `e2e-${source}.wav`;
        const wavPath = path.join(tmpDir, wavName);
        fs.writeFileSync(wavPath, Buffer.from(""));
        const dialogRpc = source === "attachment" ? "openFilePicker" : "recordAudio";

        await page.route(`**/_anki/${dialogRpc}`, (route) =>
            route.fulfill({
                contentType: "application/binary",
                body: protoStringBody(wavPath),
            }));
        await page.route("**/_anki/playFile", (route) =>
            route.fulfill({
                contentType: "application/binary",
                body: Buffer.from(""),
            }));

        // Count in the browser so both requests from the same handler are visible
        // without waiting for a duplicate request to arrive over the network.
        await page.evaluate(() => {
            const w = window as typeof window & { __mediaPlaybackCount: number };
            w.__mediaPlaybackCount = 0;
            const originalFetch = window.fetch.bind(window);
            window.fetch = (...args: Parameters<typeof fetch>) => {
                if (typeof args[0] === "string" && args[0].endsWith("/playFile")) {
                    w.__mediaPlaybackCount++;
                }
                return originalFetch(...args);
            };
        });

        const field = editableField(page, 0);
        await field.click();

        const dialogReqPromise = page.waitForRequest(isRpc(dialogRpc), { timeout: 10_000 });
        const addMediaRespPromise = page.waitForResponse(
            (resp) => isRpc("addMediaFromPath")(resp.request()) && resp.status() < 400,
            { timeout: 10_000 },
        );
        const playFileReqPromise = page.waitForRequest(isRpc("playFile"), { timeout: 10_000 });

        await (source === "attachment" ? attachButton(page) : recordButton(page)).click();

        await dialogReqPromise;
        await addMediaRespPromise;

        await expect(field).toContainText(`[sound:${wavName}]`, { timeout: 5_000 });
        const playFileReq = await playFileReqPromise;
        expect(decodeRequestBody(playFileReq, GenericString).val).toBe(wavName);
        expect(
            await page.evaluate(() =>
                (window as typeof window & { __mediaPlaybackCount: number }).__mediaPlaybackCount
            ),
        ).toBe(1);

        const calls = await bridgeCalls(page);
        expect(calls).not.toContain(source === "attachment" ? "attach" : "record");
    });
}

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
