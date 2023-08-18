// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-anki-package-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import { modalsKey } from "../components/context-keys";
import ImportAnkiPackagePage from "./ImportAnkiPackagePage.svelte";

const i18n = setupI18n({
    modules: [
        ModuleName.IMPORTING,
        ModuleName.ACTIONS,
        ModuleName.HELP,
        ModuleName.DECK_CONFIG,
        ModuleName.ADDING,
        ModuleName.EDITING,
    ],
});

export async function setupImportAnkiPackagePage(
    path: string,
): Promise<ImportAnkiPackagePage> {
    await i18n;

    const context = new Map();
    context.set(modalsKey, new Map());
    checkNightMode();

    return new ImportAnkiPackagePage({
        target: document.body,
        props: {
            path: path,
        },
        context,
    });
}

/* // use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupImportAnkiPackagePage(ntid, ntid);
} */
