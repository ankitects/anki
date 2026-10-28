// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Pasting and dropping files into the editor (#5338)
 *
 * Covers the DataTransfer.files paths in data-transfer.ts, which need a real
 * browser and backend. Text and HTML handling is covered in
 * data-transfer.test.ts.
 */

import { String as GenericString } from "@generated/anki/generic_pb";
import type { Locator, Page } from "@playwright/test";

import { expect, test } from "./fixtures";
import { decodeRequestBody, editableField, isRpc } from "./helpers";

// The backend decodes pasted images with QImage, which yields an empty file
// for an invalid PNG.
const pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGP4/x8AAwAB//wl3FEAAAAASUVORK5CYII=";

type TransferKind = "paste" | "drop";

async function transferFile(
    field: Locator,
    kind: TransferKind,
    file: { name: string; type: string; base64: string },
): Promise<void> {
    await field.evaluate((el, { kind, file }) => {
        const dt = new DataTransfer();
        const bytes = Uint8Array.from(atob(file.base64), (char) => char.charCodeAt(0));
        dt.items.add(new File([bytes], file.name, { type: file.type }));
        const init = { bubbles: true, cancelable: true };
        el.dispatchEvent(
            kind === "paste"
                ? new ClipboardEvent("paste", { ...init, clipboardData: dt })
                : new DragEvent("drop", { ...init, dataTransfer: dt }),
        );
    }, { kind, file });
}

async function fetchMedia(page: Page, filename: string): Promise<Buffer> {
    const response = await page.request.get(`/${encodeURIComponent(filename)}`);
    expect(response.ok()).toBe(true);
    return response.body();
}

for (const kind of ["paste", "drop"] as const) {
    test(`${kind} of an image file stores the image and inserts it`, async ({ editor: page }) => {
        const field = editableField(page, 0);
        await field.click();

        await transferFile(field, kind, { name: "image.png", type: "image/png", base64: pngBase64 });

        const img = field.locator("img");
        await expect(img).toHaveAttribute("src", /^paste-[0-9a-f]{40}\.jpg$/, { timeout: 10_000 });
        const stored = await fetchMedia(page, (await img.getAttribute("src"))!);
        expect(stored.length).toBeGreaterThan(0);
    });

    test(`${kind} of an audio file stores it, inserts a sound tag and plays it`, async ({ editor: page }) => {
        await page.route("**/_anki/playFile", (route) =>
            route.fulfill({
                contentType: "application/binary",
                body: Buffer.from(""),
            }));
        const name = `e2e-${kind}-${Date.now()}.mp3`;
        const audio = Buffer.from("audio bytes");

        const field = editableField(page, 0);
        await field.click();
        const playFileReqPromise = page.waitForRequest(isRpc("playFile"), { timeout: 10_000 });

        await transferFile(field, kind, { name, type: "audio/mpeg", base64: audio.toString("base64") });

        await expect(field).toContainText(`[sound:${name}]`, { timeout: 10_000 });
        expect(decodeRequestBody(await playFileReqPromise, GenericString).val).toBe(name);
        expect(await fetchMedia(page, name)).toEqual(audio);
    });
}
