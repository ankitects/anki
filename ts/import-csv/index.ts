// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-csv-base.scss";

import { getCsvMetadata, getDeckNames, getNotetypeNames } from "@tslib/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import ErrorPage from "../components/ErrorPage.svelte";
import ImportCsvPage from "./ImportCsvPage.svelte";
import { tryGetDeckColumn, tryGetDeckId, tryGetGlobalNotetype, tryGetNotetypeColumn } from "./lib";

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
    ],
});

export async function setupImportCsvPage(path: string): Promise<ImportCsvPage | ErrorPage> {
    checkNightMode();
    return Promise.all([
        getNotetypeNames({}),
        getDeckNames({
            skipEmptyDefault: false,
            includeFiltered: false,
        }),
        getCsvMetadata({ path }, { alertOnError: false }),
        i18n,
    ]).then(([notetypes, decks, metadata, _i18n]) => {
        return new ImportCsvPage({
            target: document.body,
            props: {
                path: path,
                deckNameIds: decks.entries,
                notetypeNameIds: notetypes.entries,
                dupeResolution: metadata.dupeResolution,
                matchScope: metadata.matchScope,
                delimiter: metadata.delimiter,
                forceDelimiter: metadata.forceDelimiter,
                isHtml: metadata.isHtml,
                forceIsHtml: metadata.forceIsHtml,
                globalTags: metadata.globalTags,
                updatedTags: metadata.updatedTags,
                columnLabels: metadata.columnLabels,
                tagsColumn: metadata.tagsColumn,
                guidColumn: metadata.guidColumn,
                preview: metadata.preview,
                globalNotetype: tryGetGlobalNotetype(metadata),
                // Unset oneof numbers default to 0, which also means n/a here,
                // but it's vital to differentiate between unset and 0 when reserializing.
                notetypeColumn: tryGetNotetypeColumn(metadata),
                deckId: tryGetDeckId(metadata),
                deckColumn: tryGetDeckColumn(metadata),
            },
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
