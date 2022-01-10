// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { i18n } from "../editor";
import { promiseWithResolver } from "../lib/promise";
import BrowserEditor from "./BrowserEditor.svelte";

const [uiPromise, uiResolve] = promiseWithResolver();

async function setupBrowserEditor(): Promise<void> {
    await i18n;

    new BrowserEditor({
        target: document.body,
        props: { uiResolve },
    });
}

setupBrowserEditor();

export * from "../editor";
export { uiPromise };
export const noteEditorPromise = uiPromise;
