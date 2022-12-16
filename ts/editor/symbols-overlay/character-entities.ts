// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { characterEntities } from "character-entities";

import type { SymbolsTable } from "./symbols-types";

// Not all characters work well in the editor field
delete characterEntities["Tab"];

// A single character entity can be present under different names
// So we change the mapping to symbol => name[]
const characterTable: Record<string, string[]> = {};

for (const [name, character] of Object.entries(characterEntities)) {
    if (character in characterTable) {
        characterTable[character].push(name);
    } else {
        characterTable[character] = [name];
    }
}

const characterSymbolsTable: SymbolsTable = [];

for (const [character, names] of Object.entries(characterTable)) {
    characterSymbolsTable.push({
        symbol: character,
        names,
        tags: [],
    });
}

export default characterSymbolsTable;
