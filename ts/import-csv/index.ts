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

    const [deckId, deckColumn] =
        metadata.deck === "deckId"
            ? [metadata.deckId, null]
            : [null, metadata.deckColumn];
    const [globalNotetype, notetypeColumn] =
        metadata.notetype === "globalNotetype"
            ? [metadata.globalNotetype, null]
            : [null, metadata.notetypeColumn];

    checkNightMode();

    return new ImportCsvPage({
        target: document.body,
        props: {
            path: path,
            deckNameIds: decks.entries,
            notetypeNameIds: notetypes.entries,
            delimiter: metadata.delimiter,
            forceDelimiter: metadata.forceDelimiter,
            isHtml: metadata.isHtml,
            forceIsHtml: metadata.forceIsHtml,
            tags: metadata.tags,
            columnLabels: metadata.columnLabels,
            tagsColumn: metadata.tagsColumn,
            globalNotetype: globalNotetype ?? null,
            notetypeColumn: notetypeColumn ?? null,
            deckId: deckId ?? null,
            deckColumn: deckColumn ?? null,
        },
    });
}

/* // use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupCsvImportPage(ntid, ntid);
} */
