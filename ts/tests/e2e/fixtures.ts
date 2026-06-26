// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { test as base, type Page } from "@playwright/test";

export { expect } from "@playwright/test";

interface AnkiFixtures {
    /** Page navigated to /editor/?mode=add with bridgeCommand stubbed. */
    editorPage: Page;
    /**
     * editorPage after loadNote({ initial: true }) has resolved and the first
     * field container is visible. Suitable for all editor interaction tests.
     */
    editor: Page;
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
        await editorPage.evaluate(() =>
            (window as any).loadNote({ initial: true }),
        );
        // At least one field container signals that the note loaded.
        await editorPage.waitForSelector(".field-container", { timeout: 15_000 });
        await use(editorPage);
    },
});
