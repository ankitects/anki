// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./import-csv-base.css";

import { ModuleName, setupI18n } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import {
    Decks,
    decks as decksService,
    empty,
    notetypes as notetypeService,
} from "../lib/proto";
import ImportCsvPage from "./ImportCsvPage.svelte";
import { getCsvMetadata } from "./lib";

const gettingNotetypes = notetypeService.getNotetypeNames(empty);
const gettingDecks = decksService.getDeckNames(
    Decks.GetDeckNamesRequest.create({
        skipEmptyDefault: false,
        includeFiltered: false,
    }),
);
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
    ],
});

export async function setupImportCsvPage(path: string): Promise<ImportCsvPage> {
    const gettingMetadata = getCsvMetadata(path);
    const [notetypes, decks, metadata] = await Promise.all([
        gettingNotetypes,
        gettingDecks,
        gettingMetadata,
        i18n,
    ]);

    checkNightMode();

    return new ImportCsvPage({
        target: document.body,
        props: {
            path: path,
            notetypeNameIds: notetypes.entries,
            deckNameIds: decks.entries,
            delimiter: metadata.delimiter,
            columnNames: metadata.columns,
            tags: metadata.tags,
            notetypeId: metadata.notetypeId,
            deckId: metadata.deckId,
            isHtml: metadata.isHtml!,
        },
    });
}

/* // use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupCsvImportPage(ntid, ntid);
} */
