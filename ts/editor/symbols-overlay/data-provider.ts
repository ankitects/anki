// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import symbolsTable from "./symbols";

interface SymbolsEntry {
    names: string[];
    symbol: string;
    containsHTML?: boolean;
    autoInsert?: boolean;
}

export type SymbolsTable = SymbolsEntry[];

export async function getSymbols(query: string): Promise<SymbolsTable> {
    return symbolsTable.filter(({ names }) => names.some((name) => name.includes(query)));
}

export async function getSymbolExact(query: string): Promise<string | null> {
    const found = symbolsTable.find(({ names }) => names.includes(query));

    return found ? found.symbol : null;
}
