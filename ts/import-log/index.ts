// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-log-base.scss";

import { getLastImportResponse } from "@tslib/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import ImportLogPage from "./ImportLogPage.svelte";

const i18n = setupI18n({
    modules: [ModuleName.IMPORTING, ModuleName.ADDING, ModuleName.EDITING],
});

export async function setupImportLogPage(): Promise<ImportLogPage> {
    await i18n;

    const response = await getLastImportResponse({});

    checkNightMode();

    return new ImportLogPage({
        target: document.body,
        props: {
            response,
        },
    });
}
