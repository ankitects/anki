// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

interface SymbolsEntry {
    name: string;
    symbol: string;
    containsHTML?: boolean;
    autoInsert?: boolean;
}

export type SymbolsTable = SymbolsEntry[];

// For emojis, we can generate from here https://api.github.com/emojis

const symbolsTable = [
    { name: "blush", symbol: "😊" },
    { name: "laughing", symbol: "😆" },
    { name: "rofl", symbol: "🤣" },
    { name: "joy", symbol: "😂" },
    { name: "omega", symbol: "ω" },
    { name: "Omega", symbol: "Ω" },
];

export async function getSymbols(query: string): Promise<SymbolsTable> {
    return symbolsTable.filter(({ name }) => name.includes(query));
}
