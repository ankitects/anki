// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import Fuse from "fuse.js";

import characterEntities from "./character-entities";
import gemoji from "./gemoji";
import type { SymbolsEntry, SymbolsTable } from "./symbols-types";

const symbolsTable: SymbolsTable = [...characterEntities, ...gemoji];

const symbolsFuse = new Fuse(symbolsTable, {
    threshold: 0.2,
    minMatchCharLength: 2,
    useExtendedSearch: true,
    isCaseSensitive: true,
    keys: [
        {
            name: "names",
            weight: 7,
        },
        {
            name: "tags",
            weight: 3,
        },
        {
            name: "autoInsert",
            weight: 0.1,
        },
        {
            name: "containsHTML",
            weight: 0.1,
        },
    ],
});

export function findSymbols(query: string): SymbolsTable {
    return symbolsFuse.search(query).map(({ item }) => item);
}

export function getExactSymbol(query: string): SymbolsEntry | null {
    const [found] = symbolsFuse.search({ names: `="${query}"` }, { limit: 1 });

    return found ? found.item : null;
}

export function getAutoInsertSymbol(query: string): SymbolsEntry | null {
    const [found] = symbolsFuse.search({
        names: `="${query}"`,
        autoInsert: "=autoInsert",
    });

    return found ? found.item : null;
}
