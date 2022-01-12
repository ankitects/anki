// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { i18n } from ".";
import BrowserEditor from "./BrowserEditor.svelte";
import { promiseWithResolver } from "../lib/promise";
import { globalExport } from "../lib/globals";

const [uiPromise, uiResolve] = promiseWithResolver();

async function setupBrowserEditor(): Promise<void> {
    await i18n;

    new BrowserEditor({
        target: document.body,
        props: { uiResolve },
    });
}

setupBrowserEditor();

import * as editor from ".";

globalExport({
    ...editor,
    uiPromise,
    noteEditorPromise: uiPromise,
});
