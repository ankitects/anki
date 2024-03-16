// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-csv-base.scss";

import { getCsvMetadata, getDeckNames, getNotetypeNames } from "@generated/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import { modalsKey } from "$lib/components/context-keys";
import ErrorPage from "$lib/components/ErrorPage.svelte";

import ImportCsvPage from "./ImportCsvPage.svelte";
import { ImportCsvState } from "./lib";

const i18n = setupI18n({
    modules: [
        ModuleName.ACTIONS,
        ModuleName.CHANGE_NOTETYPE,
        ModuleName.DECKS,
        ModuleName.EDITING,
        ModuleName.IMPORTING,
        ModuleName.KEYBOARD,
        ModuleName.NOTETYPES,
        ModuleName.STUDYING,
        ModuleName.ADDING,
        ModuleName.HELP,
        ModuleName.DECK_CONFIG,
    ],
});

export async function setupImportCsvPage(path: string): Promise<ImportCsvPage | ErrorPage> {
    const context = new Map();
    context.set(modalsKey, new Map());
    checkNightMode();

    return Promise.all([
        getNotetypeNames({}),
        getDeckNames({
            skipEmptyDefault: false,
            includeFiltered: false,
        }),
        getCsvMetadata({ path }, { alertOnError: false }),
        i18n,
    ]).then(([notetypes, decks, metadata]) => {
        return new ImportCsvPage({
            target: document.body,
            props: {
                state: new ImportCsvState(path, notetypes, decks, metadata),
            },
            context,
        });
    }).catch((error) => {
        return new ErrorPage({ target: document.body, props: { error } });
    });
}

/* // use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupCsvImportPage(ntid, ntid);
} */
