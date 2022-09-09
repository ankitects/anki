// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { gemoji } from "gemoji";

import type { SymbolsTable } from "./symbols-types";

const gemojiSymbolsTable: SymbolsTable = [];

for (const { emoji, names, tags } of gemoji) {
    gemojiSymbolsTable.push({
        symbol: emoji,
        names,
        tags,
    });
}

export default gemojiSymbolsTable;
