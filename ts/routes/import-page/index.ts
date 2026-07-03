// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-page-base.scss";

import { importJsonFile, importJsonString } from "@generated/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import ImportPage from "./ImportPage.svelte";
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

const postOptions = { alertOnError: false };

export async function setupImportPage(
    params: LogParams,
): Promise<ImportPage> {
    await i18n;

    checkNightMode();

    return new ImportPage({
        target: document.body,
        props: {
            path: params.path,
            noOptions: true,
            importer: {
                doImport: () => {
                    switch (params.type) {
                        case "json_file":
                            return importJsonFile({ val: params.path }, postOptions);
                        case "json_string":
                            return importJsonString({ val: params.json }, postOptions);
                    }
                },
            },
        },
    });
}

if (window.location.hash.startsWith("#test-")) {
    const path = window.location.hash.replace("#test-", "");
    setupImportPage({ type: "json_file", path });
}
