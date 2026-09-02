// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Suite – paste & drop data-transfer handling (issue #5338)
 *
 * Verifies that copy/paste and drag & drop in the experimental editor handle
 * various data types correctly end-to-end. This covers the fixes from:
 *
 *   - #5337: fallback to DataTransfer.files for audio (processAudioFiles)
 *   - #5329: pasted/dropped audio triggers playFile and produces [sound:...]
 *
 * The code under test lives in:
 *   ts/routes/editor/rich-text-input/data-transfer.ts
 *
 * Related but separate: paste-filter.spec.ts tests HTML sanitization/filtering.
 * This suite tests the data-transfer pipeline: what happens when different
 * clipboard content types (HTML, plain text, files) arrive via paste or drop.
 */

import { AddNoteRequest } from "@generated/anki/notes_pb";

import { expect, test } from "./fixtures";
import { decodeRequestBody, editableField, isRpc, pasteData } from "./helpers";
import type { Locator } from "@playwright/test";

// ---------------------------------------------------------------------------
// Helpers for file-based paste/drop
//
// The existing pasteData() helper only handles string MIME types via
// DataTransfer.setData(). For file-based pastes (images, audio), we need to
// construct File objects and add them to DataTransfer.items/files.
// ---------------------------------------------------------------------------

/**
 * Simulate pasting a file by dispatching a ClipboardEvent with a File in
 * DataTransfer.items. This exercises the getImageData() and getAudioFile()
 * fallback paths that read from DataTransfer.files.
 */
async function pasteFile(
    locator: Locator,
    filename: string,
    mimeType: string,
    content: number[],
): Promise<void> {
    await locator.evaluate(
        (el, { filename, mimeType, content }) => {
            const dt = new DataTransfer();
            const file = new File(
                [new Uint8Array(content)],
                filename,
                { type: mimeType },
            );
            dt.items.add(file);
            el.dispatchEvent(
                new ClipboardEvent("paste", {
                    clipboardData: dt,
                    bubbles: true,
                    cancelable: true,
                }),
            );
        },
        { filename, mimeType, content },
    );
}

/**
 * Simulate dropping a file by dispatching a DragEvent with a File in
 * DataTransfer.items.
 */
async function dropFile(
    locator: Locator,
    filename: string,
    mimeType: string,
    content: number[],
): Promise<void> {
    await locator.evaluate(
        (el, { filename, mimeType, content }) => {
            const dt = new DataTransfer();
            const file = new File(
                [new Uint8Array(content)],
                filename,
                { type: mimeType },
            );
            dt.items.add(file);
            el.dispatchEvent(
                new DragEvent("drop", {
                    dataTransfer: dt,
                    bubbles: true,
                    cancelable: true,
                }),
            );
        },
        { filename, mimeType, content },
    );
}

// Minimal valid PNG (1x1 pixel, transparent) — enough for the backend to
// accept it as a real image without erroring on decode.
const TINY_PNG = [
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, // PNG signature
    0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
    0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
    0x08, 0x06, 0x00, 0x00, 0x00, 0x1f, 0x15, 0xc4, 0x89, // RGBA, CRC
    0x00, 0x00, 0x00, 0x0a, 0x49, 0x44, 0x41, 0x54, // IDAT chunk
    0x78, 0x9c, 0x62, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01, 0xe5, // compressed data
    0x27, 0xde, 0xfc,                                 // CRC
    0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44, // IEND chunk
    0xae, 0x42, 0x60, 0x82,                           // CRC
];

// Minimal MP3 frame — just enough bytes that the file extension triggers
// audio handling (the backend stores it as-is without decoding audio data).
const TINY_MP3 = [
    0xff, 0xfb, 0x90, 0x00, // MP3 frame header (MPEG1, Layer3, 128kbps, 44100Hz)
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
];

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test("pasting plain text inserts HTML-escaped content", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteData(field, {
        "text/plain": "Hello <world> & \"friends\"",
    });

    // The text must appear in the field, HTML-escaped.
    await expect(field).toContainText("Hello <world>", { timeout: 5_000 });

    const innerHTML = await field.evaluate((el) => el.innerHTML);
    // Angle brackets and ampersands must be escaped.
    expect(innerHTML).toContain("&lt;world&gt;");
    expect(innerHTML).toContain("&amp;");
});

test("pasting HTML preserves markup in the field", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteData(field, {
        "text/html": "<b>Bold</b> and <i>italic</i>",
    });

    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).toContain("<b>Bold</b>");
    expect(innerHTML).toContain("<i>italic</i>");
});

test("pasting HTML is saved correctly in addNote payload", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteData(field, {
        "text/html": "<b>Important</b> stuff",
    });

    await expect(field).toContainText("Important", { timeout: 5_000 });

    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), {
        timeout: 10_000,
    });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await page.waitForResponse(
        (resp) => isRpc("addNote")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );

    const decoded = decodeRequestBody(await addNoteReqPromise, AddNoteRequest);
    expect(decoded.note?.fields[0]).toContain("<b>Important</b>");
});

test("pasting an image file inserts an <img> tag", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteFile(field, "test-image.png", "image/png", TINY_PNG);

    // The editor should process the image and insert an <img> tag.
    // The filename will be a paste-<checksum>.jpg/png generated by addPastedImage.
    await expect(field.locator("img")).toBeAttached({ timeout: 10_000 });

    const src = await field.locator("img").getAttribute("src");
    expect(src).toMatch(/paste-[0-9a-f]+\.(jpg|png)/);
});

test("pasting an audio file inserts a [sound:...] tag (#5337)", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteFile(field, "test-audio.mp3", "audio/mpeg", TINY_MP3);

    // The editor should process the audio file via getAudioFile() fallback
    // and insert a [sound:filename] tag. Wait for field content to appear.
    await expect(field).not.toHaveText("", { timeout: 10_000 });

    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).toMatch(/\[sound:.+\.mp3\]/);
});

test("pasted audio file is saved in addNote payload as [sound:...] (#5337)", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteFile(field, "clip.mp3", "audio/mpeg", TINY_MP3);

    await expect(field).not.toHaveText("", { timeout: 10_000 });

    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), {
        timeout: 10_000,
    });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await page.waitForResponse(
        (resp) => isRpc("addNote")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );

    const decoded = decodeRequestBody(await addNoteReqPromise, AddNoteRequest);
    expect(decoded.note?.fields[0]).toMatch(/\[sound:.+\.mp3\]/);
});

test("dropping an audio file inserts a [sound:...] tag", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await dropFile(field, "dropped-audio.mp3", "audio/mpeg", TINY_MP3);

    await expect(field).not.toHaveText("", { timeout: 10_000 });

    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).toMatch(/\[sound:.+\.mp3\]/);
});

test("dropping an image file inserts an <img> tag", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await dropFile(field, "dropped-image.png", "image/png", TINY_PNG);

    await expect(field.locator("img")).toBeAttached({ timeout: 10_000 });

    const src = await field.locator("img").getAttribute("src");
    expect(src).toMatch(/paste-[0-9a-f]+\.(jpg|png)/);
});

test("pasting text/html takes priority over text/plain when both are present", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    // When both HTML and plain text are in the clipboard (common when copying
    // from a browser), the HTML path should be used.
    await pasteData(field, {
        "text/html": "<em>Rich</em> content",
        "text/plain": "Rich content",
    });

    const innerHTML = await field.evaluate((el) => el.innerHTML);
    // The HTML version should win — we should see the <em> tag.
    expect(innerHTML).toContain("<em>Rich</em>");
});
