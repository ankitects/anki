// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

interface SymbolsEntry {
    query: string,
    symbol: string,
    containsHTML?: boolean,
    autoInsert?: boolean,
}

type SymbolsTable = SymbolsEntry[];

// For emojis, we can generate from here https://api.github.com/emojis

export async function getSymbols(): Promise<SymbolsTable> {
    return [
        { query: "blush", symbol: "😊" },
        { query: "laughing", symbol: "😆" },
        { query: "rofl", symbol: "🤣" },
        { query: "joy", symbol: "😂" },
        { query: "omega", symbol: "ω" },
        { query: "Omega", symbol: "Ω" },
    ]
}
