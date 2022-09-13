// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
