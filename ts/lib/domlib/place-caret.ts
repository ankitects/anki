// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection } from "@tslib/cross-browser";

function placeCaret(node: Node, range: Range): void {
    const selection = getSelection(node)!;
    selection.removeAllRanges();
    selection.addRange(range);
}

export function placeCaretBefore(node: Node): void {
    const range = new Range();
    range.setStartBefore(node);
    range.collapse(true);

    placeCaret(node, range);
}

export function placeCaretAfter(node: Node): void {
    const range = new Range();
    range.setStartAfter(node);
    range.collapse(true);

    placeCaret(node, range);
}

export function placeCaretAfterContent(node: Node): void {
    const range = new Range();
    range.selectNodeContents(node);
    range.collapse(false);

    placeCaret(node, range);
}
