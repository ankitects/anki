// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { getCurrentField, setFormat } from ".";

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/)!;
    return match[1] + front + match[2] + back + match[3];
}

function moveCursorPastPostfix(selection: Selection, postfix: string): void {
    const range = selection.getRangeAt(0);
    range.setStart(range.startContainer, range.startOffset - postfix.length);
    range.collapse(true);
    selection.removeAllRanges();
    selection.addRange(range);
}

function wrapInternal(front: string, back: string, plainText: boolean): void {
    const currentField = getCurrentField()!;
    const selection = currentField.getSelection();
    const range = selection.getRangeAt(0);
    const content = range.cloneContents();
    const span = document.createElement("span");
    span.appendChild(content);

    if (plainText) {
        const new_ = wrappedExceptForWhitespace(span.innerText, front, back);
        setFormat("inserttext", new_);
    } else {
        const new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
        setFormat("inserthtml", new_);
    }

    if (!span.innerHTML) {
        moveCursorPastPostfix(selection, back);
    }
}

export function wrap(front: string, back: string): void {
    wrapInternal(front, back, false);
}

export function wrapCurrent(front: string, back: string): void {
    const currentField = getCurrentField()!;
    currentField.surroundSelection(front, back);
}

/* currently unused */
export function wrapIntoText(front: string, back: string): void {
    wrapInternal(front, back, true);
}
