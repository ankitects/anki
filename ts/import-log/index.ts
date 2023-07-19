// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-log-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import ImportLogPage from "./ImportLogPage.svelte";
import type { LogParams } from "./types";

const i18n = setupI18n({
    modules: [
        ModuleName.IMPORTING,
        ModuleName.ADDING,
        ModuleName.EDITING,
        ModuleName.ACTIONS,
        ModuleName.KEYBOARD,
    ],
});

export async function setupImportLogPage(
    params: LogParams,
): Promise<ImportLogPage> {
    await i18n;

    checkNightMode();

    return new ImportLogPage({
        target: document.body,
        props: {
            params,
        },
    });
}

if (window.location.hash.startsWith("#test-")) {
    const path = window.location.hash.replace("#test-", "");
    setupImportLogPage({ type: "apkg", path });
}
