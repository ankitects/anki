// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { characterEntities } from "character-entities";
import Fuse from "fuse.js";
import { gemoji } from "gemoji";

export interface SymbolsEntry {
    symbol: string;
    /**
     * Used for searching and direct insertion
     */
    names: string[];
    /**
     * Used for searching
     */
    tags: string[];
    /**
     * If symbols contain HTML, they need to be treated specially.
     */
    containsHTML?: "containsHTML";
    /**
     * Symbols can be automak inserted, when you enter a full name within delimiters.
     * If you enable auto insertion, you can use direction insertion without
     * using the delimiter and triggering the search dropdown.
     * To falicitate interacting with fuse.js this is a string value rather than a boolean.
     */
    autoInsert?: "autoInsert";
}

export type SymbolsTable = SymbolsEntry[];

const symbolsTable: SymbolsTable = [];

const characterTable: Record<string, string[]> = {};

// Not all characters work well in the editor field
delete characterEntities["Tab"];

for (const [name, character] of Object.entries(characterEntities)) {
    if (character in characterTable) {
        characterTable[character].push(name);
    } else {
        characterTable[character] = [name];
    }
}

for (const [character, names] of Object.entries(characterTable)) {
    symbolsTable.push({
        symbol: character,
        names,
        tags: [],
    });
}

for (const { emoji, names, tags } of gemoji) {
    symbolsTable.push({
        symbol: emoji,
        names,
        tags,
    });
}


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
