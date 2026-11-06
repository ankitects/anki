// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "./cross-browser";
import { untrustedHTML } from "./trusted-types";

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const normalizedText = text
        .replace(/&nbsp;/g, " ")
        .replace(/&#160;/g, " ")
        .replace(/\u00A0/g, " ");

    const match = normalizedText.match(/^(\s*)([^]*?)(\s*)$/)!;
    return match[1] + front + match[2] + back + match[3];
}

function moveCursorInside(selection: Selection, postfix: string): void {
    const range = getRange(selection)!;

    range.setEnd(range.endContainer, range.endOffset - postfix.length);
    range.collapse(false);

    selection.removeAllRanges();
    selection.addRange(range);
}

export function wrapInternal(
    base: Element,
    front: string,
    back: string,
    plainText: boolean,
): void {
    const selection = getSelection(base)!;
    const range = getRange(selection);

    if (!range) {
        return;
    }

    const wasCollapsed = range.collapsed;
    const content = range.cloneContents();
    const span = document.createElement("span");
    span.appendChild(content);

    if (plainText) {
        const new_ = wrappedExceptForWhitespace(span.innerText, front, back);
        document.execCommand("inserttext", false, untrustedHTML(new_) as unknown as string);
    } else {
        const new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
        document.execCommand("inserthtml", false, untrustedHTML(new_) as unknown as string);
    }

    if (
        wasCollapsed
        /* ugly solution: treat <anki-mathjax> differently than other wraps */ && !front.includes(
            "<anki-mathjax",
        )
    ) {
        moveCursorInside(selection, back);
    }
}
