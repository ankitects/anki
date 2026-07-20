// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Regression test for #4754: a keystroke in another field within 600 ms of
 * the last mask edit cancelled the pending debounced save of the Occlusions
 * field, and it was never re-scheduled, so masks drawn since the last
 * commit were missing from the added note.
 *
 * Mutates the collection: adds the Image Occlusion notetype and notes.
 */

import { AddImageOcclusionNoteRequest } from "@generated/anki/image_occlusion_pb";
import { AddNoteRequest } from "@generated/anki/notes_pb";
import type { Page } from "@playwright/test";
import path from "path";

import { expect, test } from "./fixtures";
import { callRpc, chooserButton, decodeRequestBody, editableField, isRpc, openChooserAndSelect } from "./helpers";

// stock image occlusion notetype field order (rslib/src/image_occlusion/notetype.rs)
const OCCLUSIONS_FIELD = 0;
const HEADER_FIELD = 2;

const TEST_IMAGE = path.resolve("qt/aqt/data/web/imgs/anki-logo-thin.png");

function clozeCount(field: string): number {
    return (field.match(/image-occlusion:/g) ?? []).length;
}

function shapeCount(page: Page): Promise<number> {
    return page.evaluate(() => {
        const canvas = (globalThis as any).canvas;
        return canvas.getObjects().filter((o: any) => o.id !== "boundingBox").length;
    });
}

async function drawRect(
    page: Page,
    box: { x: number; y: number },
    from: [number, number],
    to: [number, number],
    expectedShapes: number,
): Promise<void> {
    // a drag occasionally fails to register while the canvas layout is
    // still settling, so verify the shape landed and retry if not
    for (let attempt = 0; attempt < 3; attempt++) {
        await page.mouse.move(box.x + from[0], box.y + from[1]);
        await page.mouse.down();
        await page.mouse.move(box.x + to[0], box.y + to[1], { steps: 3 });
        await page.mouse.up();
        if (await shapeCount(page) === expectedShapes) {
            return;
        }
        await page.waitForTimeout(300);
    }
    throw new Error("failed to draw a rectangle on the mask canvas");
}

// Leaving Image Occlusion as the current notetype would strand later tests
// in the mask picker view. The switch only persists once a note is added.
test.afterEach(async ({ editorPage: page }) => {
    try {
        await chooserButton(page, "notetype").click();
        const modal = page.locator(".modal.show");
        await modal.waitFor({ state: "visible", timeout: 5_000 });
        await modal.getByRole("button", { name: "Select Basic", exact: true }).click();
        await modal.waitFor({ state: "hidden", timeout: 5_000 });
        const field = editableField(page, 0);
        await field.click();
        await field.pressSequentially("io-race cleanup");
        const added = page.waitForRequest(isRpc("addNote"), { timeout: 5_000 });
        await page.getByRole("button", { name: "Add", exact: true }).click();
        await added;
    } catch {
        // best effort
    }
});

test("masks drawn shortly before typing in another field are saved", async ({ editor: page }) => {
    // addImageOcclusionNote with notetypeId 0 creates the notetype on demand
    await callRpc(
        page,
        "addImageOcclusionNote",
        new AddImageOcclusionNoteRequest({
            notetypeId: 0n,
            imagePath: TEST_IMAGE,
            occlusions: "{{c1::image-occlusion:rect:left=.1:top=.1:width=.2:height=.2}}",
            header: "seed",
            backExtra: "",
            tags: [],
        }),
        1,
    );
    await openChooserAndSelect(page, "notetype", "Image Occlusion");
    // the modal's fade-out backdrop intercepts pointer events
    await page.waitForFunction(
        () => !document.querySelector(".modal.show") && !document.querySelector(".modal-backdrop"),
        { timeout: 5_000 },
    );

    await page.evaluate(
        (imagePath) => (globalThis as any).setupMaskEditorForNewNote(imagePath),
        TEST_IMAGE,
    );
    // canvas is ready once the bounding box has been added
    await page.waitForFunction(() => {
        const canvas = (globalThis as any).canvas;
        return canvas && canvas.getObjects && canvas.getObjects().length >= 1;
    }, { timeout: 15_000 });

    // shapes must be drawn inside the image's bounding box
    const imageBox = await page.locator("#image").boundingBox();
    expect(imageBox).not.toBeNull();
    const rectA: [[number, number], [number, number]] = [
        [imageBox!.width * 0.1, imageBox!.height * 0.2],
        [imageBox!.width * 0.3, imageBox!.height * 0.7],
    ];
    const rectB: [[number, number], [number, number]] = [
        [imageBox!.width * 0.5, imageBox!.height * 0.2],
        [imageBox!.width * 0.7, imageBox!.height * 0.7],
    ];

    // rectangle A, allowed to commit normally
    await drawRect(page, imageBox!, ...rectA, 1);
    await page.waitForTimeout(900);

    const toggleButton = page.locator("#io-mask-btn");
    await expect(toggleButton).toBeVisible();

    // rectangle B, then switch to the fields view and get a Header keystroke
    // in before the debounced occlusions save fires (600 ms after mouse-up)
    await drawRect(page, imageBox!, ...rectB, 2);
    const drawnAt = Date.now();
    await toggleButton.click();
    await editableField(page, HEADER_FIELD).click();
    await page.keyboard.type("s");
    // past the debounce window the test passes without exercising the race
    expect(Date.now() - drawnAt).toBeLessThan(600);

    await page.waitForTimeout(900);

    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), { timeout: 10_000 });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    const decoded = decodeRequestBody(await addNoteReqPromise, AddNoteRequest);

    expect(clozeCount(decoded.note!.fields[OCCLUSIONS_FIELD])).toBe(2);
});
