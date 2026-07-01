// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Locator, Page, Request } from "@playwright/test";

// ---------------------------------------------------------------------------
// RPC URL helpers
// ---------------------------------------------------------------------------

export function rpcUrl(method: string): string {
    return `/_anki/${method}`;
}

/**
 * Returns a Playwright request predicate that matches only this exact RPC
 * endpoint. Uses endsWith() so "addNote" never accidentally matches
 * "addNoteBulk" or similar future methods.
 */
export function isRpc(method: string): (req: Request) => boolean {
    const suffix = rpcUrl(method);
    return (req) => req.url().endsWith(suffix);
}

// ---------------------------------------------------------------------------
// Field locators
//
// DOM shape (NoteEditor.svelte → EditorField.svelte → RichTextInput.svelte):
//
//   .field-container[data-index="N"]          ← EditorField.svelte:97,102
//     .rich-text-editable                     ← RichTextInput.svelte:245
//       #shadow-root (open, via attachShadow)
//         anki-editable[contenteditable=true] ← ContentEditable.svelte:44
// ---------------------------------------------------------------------------

export function fieldContainer(page: Page, index: number): Locator {
    return page.locator(`.field-container[data-index="${index}"]`);
}

/**
 * The contenteditable element for a field. Playwright pierces open shadow roots
 * automatically through chained locator() calls, so no special pierce syntax is
 * needed.
 */
export function editableField(page: Page, index: number): Locator {
    return fieldContainer(page, index)
        .locator(".rich-text-editable")
        .locator("anki-editable[contenteditable='true']");
}

// ---------------------------------------------------------------------------
// Bridge call inspection
// ---------------------------------------------------------------------------

export function bridgeCalls(page: Page): Promise<string[]> {
    return page.evaluate(() => (window as any).__bridgeCalls as string[]);
}

// ---------------------------------------------------------------------------
// Protobuf decode
//
// The generated types (proto3.makeMessageType) expose a static fromBinary()
// on the returned object. Pass the message class/type directly and it decodes
// the binary postData from the intercepted Playwright Request.
//
// Requires that out/ts/lib/generated/ was built (./ninja ts or equivalent).
// ---------------------------------------------------------------------------

type BinaryDecodable<T> = {
    fromBinary(bytes: Uint8Array): T;
};

export function decodeRequestBody<T>(
    request: Request,
    messageType: BinaryDecodable<T>,
): T {
    const body = request.postDataBuffer();
    if (!body) {
        throw new Error(`Request to ${request.url()} had no postData`);
    }
    try {
        return messageType.fromBinary(new Uint8Array(body));
    } catch (e) {
        throw new Error(`Failed to decode protobuf from ${request.url()}: ${e}`);
    }
}

// ---------------------------------------------------------------------------
// Paste helper
//
// The editor's handlePaste (data-transfer.ts) reads from event.clipboardData,
// which is only available inside the browser context. The event must therefore
// be constructed and dispatched via page.evaluate / locator.evaluate.
// ---------------------------------------------------------------------------

export async function pasteData(
    locator: Locator,
    data: Record<string, string>,
): Promise<void> {
    await locator.evaluate((el, data) => {
        const dt = new DataTransfer();
        for (const [mimeType, value] of Object.entries(data)) {
            dt.setData(mimeType, value);
        }
        el.dispatchEvent(
            new ClipboardEvent("paste", {
                clipboardData: dt,
                bubbles: true,
                cancelable: true,
            }),
        );
    }, data);
}
