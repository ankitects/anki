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
            deckNameIds: decks.entries,
            notetypeNameIds: notetypes.entries,
            delimiter: metadata.delimiter,
            isHtml: metadata.isHtml!,
            tags: metadata.tags,
            columnLabels: metadata.columnLabels,
            tagsColumn: metadata.tagsColumn,
            globalNotetype: metadata.globalNotetype ? metadata.globalNotetype : null,
            notetypeColumn: metadata.notetypeColumn ? metadata.notetypeColumn : null,
            deckId: metadata.deckId ? metadata.deckId : null,
            deckColumn: metadata.deckColumn ? metadata.deckColumn : null,
        },
    });
}

/* // use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupCsvImportPage(ntid, ntid);
} */
