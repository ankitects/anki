// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { characterEntities } from "character-entities";
import { gemoji } from "gemoji";

interface SymbolsEntry {
    symbol: string;
    names: string[];
    tags: string[];
    containsHTML?: boolean;
    autoInsert?: boolean;
}

export type SymbolsTable = SymbolsEntry[];

const symbolsTable: SymbolsTable = [];

const characterTable: Record<string, string[]> = {};

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

export async function getSymbols(query: string): Promise<SymbolsTable> {
    return symbolsTable.filter(
        ({ names, tags }) =>
            names.some((name) => name.includes(query)) ||
            tags.some((tag) => tag.includes(query)),
    );
}

export async function getSymbolExact(query: string): Promise<string | null> {
    const found = symbolsTable.find(({ names }) => names.includes(query));

    return found ? found.symbol : null;
}
