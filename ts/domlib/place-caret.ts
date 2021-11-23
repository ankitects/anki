// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection } from "../lib/cross-browser";

export function placeCaretAfter(node: Node): void {
    const range = new Range();
    range.setStartAfter(node);
    range.collapse(false);

    const selection = getSelection(node)!;
    selection.removeAllRanges();
    selection.addRange(range);
}
