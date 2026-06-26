// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Suite 3 – HTML paste filtering (issue #4930)
 *
 * Verifies that the TypeScript html-filter (ts/lib/html-filter/element.ts)
 * actually runs end-to-end on paste, producing the same sanitised output that
 * the legacy Python _pastePreFilter (editor_legacy.py:1062) did.
 *
 * The unit-level characterisation tests live in index.test.ts (vitest/jsdom).
 * This suite verifies the integration: paste event → filterHTML → insertHTML
 * → addNote payload.
 *
 * Test case 1 – <p> → <div> conversion
 *   Source: element.ts:59  `P: convertToDiv`
 *   Legacy: editor_legacy.py:1076-1079  `for node in doc("p"): node.name = "div"`
 *
 * Test case 2 – <script> stripped (no XSS, no execution)
 *   Source: element.ts:101-105  unknown tag with innerHTML → unwrapElement
 *           (content becomes a text node — intentional difference from legacy
 *           .decompose(), documented in index.test.ts:63-71)
 *   Legacy: editor_legacy.py:1060  `removeTags = ["script", ...]`
 *
 * Test case 3 – event handler attributes stripped (no XSS via onclick/onerror)
 *   Source: element.ts:14-25  filterAttributes() with allowlist — any attribute
 *           not in the allowlist is removed, including all on* handlers.
 *   Legacy: editor_legacy.py:1062-1107  _pastePreFilter via BeautifulSoup
 *           (attribute removal was handled by the JS layer via execCommand).
 *
 * Note: The paste event must be constructed inside page.evaluate() so that
 * DataTransfer is a real browser API (the Node-side constructor does not
 * populate clipboardData). See helpers.pasteData().
 */

import { AddNoteRequest } from "@generated/anki/notes_pb";

import { expect, test } from "./fixtures";
import { decodeRequestBody, editableField, isRpc, pasteData, rpcUrl } from "./helpers";

test("<p> tags in pasted HTML are converted to <div> by the TS filter", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });
    await field.click();

    await pasteData(field, {
        "text/html": "<p>Paragraph One</p><p>Paragraph Two</p>",
    });

    // Filter must have rewritten <p> → <div> (element.ts:37-41, convertToDiv).
    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).toContain("<div>Paragraph One</div>");
    expect(innerHTML).toContain("<div>Paragraph Two</div>");
    expect(innerHTML).not.toMatch(/<p/i);

    // The saved payload must also contain <div> and not <p>.
    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), { timeout: 10_000 });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await page.waitForResponse(
        (resp) => isRpc("addNote")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );
    const decoded = decodeRequestBody(await addNoteReqPromise, AddNoteRequest);
    expect(decoded.note?.fields[0]).toContain("<div>Paragraph One</div>");
    expect(decoded.note?.fields[0]).not.toMatch(/<p/i);
});

test("pasted <script> tags are stripped and do not execute", async ({ editor: page }) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });

    // Arm the XSS sentinel before paste so we can assert it never fires.
    await page.evaluate(() => {
        (window as unknown as { __xssRan?: boolean }).__xssRan = false;
    });

    await field.click();
    await pasteData(field, {
        "text/html": "<p>Safe Content</p><script>window.__xssRan = true;<\/script>",
    });

    // The text from the safe paragraph must appear.
    await expect(field).toContainText("Safe Content", { timeout: 5_000 });

    // No <script> element may survive in the DOM.
    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).not.toMatch(/<script/i);

    // The script must never have executed.
    expect(
        await page.evaluate(
            () => (window as unknown as { __xssRan?: boolean }).__xssRan,
        ),
        "pasted <script> must never execute",
    ).toBe(false);

    // The script content must NOT appear as visible text in the field.
    // The legacy Python filter (editor_legacy.py:1072-1074) used BeautifulSoup
    // .decompose() which removes tag + content entirely. The TS filter
    // (html-filter/element.ts:101-105) currently uses unwrapElement(), which
    // keeps content as a text node. This assertion enforces parity with the
    // legacy behavior: script text must be fully discarded.
    // FIX REQUIRED: change the SCRIPT handling in element.ts from
    // unwrapElement to removeElement (same as TITLE) so content is dropped.
    await expect(field).not.toContainText("window.__xssRan", { timeout: 5_000 });

    // The saved note payload must also be free of <script> tags and content.
    const addNoteReqPromise = page.waitForRequest(isRpc("addNote"), { timeout: 10_000 });
    await page.getByRole("button", { name: "Add", exact: true }).click();
    await page.waitForResponse(
        (resp) => isRpc("addNote")(resp.request()) && resp.status() < 400,
        { timeout: 10_000 },
    );
    const decoded = decodeRequestBody(await addNoteReqPromise, AddNoteRequest);
    expect(decoded.note?.fields[0]).not.toMatch(/<script/i);
    expect(decoded.note?.fields[0]).not.toContain("window.__xssRan");
});

test("event handler attributes in pasted HTML are stripped and never execute", async ({
    editor: page,
}) => {
    const field = editableField(page, 0);
    await expect(field).toBeAttached({ timeout: 10_000 });

    // Arm XSS sentinel before paste.
    await page.evaluate(() => {
        (window as unknown as { __xssRan?: boolean }).__xssRan = false;
    });

    await field.click();
    await pasteData(field, {
        // onclick on a div and onerror on an img — both are on* event handlers
        // that the allowlist-based filter (element.ts:14-25) must strip.
        "text/html":
            '<div onclick="window.__xssRan = true">Safe Content</div>' +
            '<img src="x" onerror="window.__xssRan = true">',
    });

    // The safe text content must appear.
    await expect(field).toContainText("Safe Content", { timeout: 5_000 });

    // No on* attribute may survive in the DOM.
    const innerHTML = await field.evaluate((el) => el.innerHTML);
    expect(innerHTML).not.toMatch(/\bon\w+=/i);

    // Handlers must never have executed.
    expect(
        await page.evaluate(
            () => (window as unknown as { __xssRan?: boolean }).__xssRan,
        ),
        "pasted event handlers must never execute",
    ).toBe(false);
});
