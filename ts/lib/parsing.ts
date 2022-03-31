// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Parsing with or without this dummy structure changes the output
 * for both `DOMParser.parseAsString` and range.createContextualFragment`.
 * Parsing without means that comments or meaningless html elements are dropped,
 * which we want to avoid.
 */
export function createDummyDoc(html: string): string {
    return `<html><head></head><body>${html}</body></html>`;
}
