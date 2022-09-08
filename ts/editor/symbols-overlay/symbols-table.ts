// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { characterEntities } from "character-entities";
import Fuse from "fuse.js";
import { gemoji } from "gemoji";

interface SymbolsEntry {
    symbol: string;
    /**
     * Used for searching and direct replacement
     */
    names: string[];
    /**
     * Used for searching
     */
    tags: string[];
    /**
     * If symbols contain HTML, they need to be treated specially.
     */
    containsHTML?: boolean;
    /**
     * Symbols can be directly inserted, when you enter a full name within delimiters.
     * If you enable auto replacement, you can use direction replacement without
     * using the delimiter and triggering the search dropdown.
     */
    autoReplace?: boolean;
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
    isCaseSensitive: true,
    keys: [
        {
            name: "names",
            weight: 0.7,
        },
        {
            name: "tags",
            weight: 0.3,
        },
    ],
});

export function findSymbols(query: string): SymbolsTable {
    return symbolsFuse.search(query).map(({ item }) => item);
}

export function getExactSymbol(query: string): string | null {
    const found = symbolsTable.find(({ names }) => names.includes(query));

    return found ? found.symbol : null;
}
