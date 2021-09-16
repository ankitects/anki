// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EditingArea } from "./editing-area";

export function getCurrentField(): EditingArea | null {
    return document.activeElement?.closest(".field") ?? null;
}

export function appendInParentheses(text: string, appendix: string): string {
    return `${text} (${appendix})`;
}
