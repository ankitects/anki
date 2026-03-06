// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function globalExport(globals: Record<string, unknown>): void {
    for (const key in globals) {
        window[key] = globals[key];
    }

    // but also export as window.anki
    window["anki"] = window["anki"] || {};
    window["anki"] = { ...window["anki"], ...globals };
}
