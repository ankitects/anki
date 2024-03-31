// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { placeCaretAfter } from "../domlib/place-caret";
import { getRange, getSelection } from "./cross-browser";

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/)!;
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
        document.execCommand("inserttext", false, new_);
    } else {
        const new_ = wrappedExceptForWhitespace(span.innerHTML, front, back);
        document.execCommand("inserthtml", false, new_);
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

export function wrapClozeInternal(base: Element, n: number): void {
    const selection = getSelection(base)!;
    const range = getRange(selection);
    if (!range) {
        return;
    }

    const fragment = range.extractContents();
    if (fragment.childNodes.length === 0) {
        document.execCommand("inserthtml", false, `{{c${n}::}}`);
        moveCursorInside(selection, "}}");
    } else {
        const startNode = document.createTextNode(`{{c${n}::`);
        const endNode = document.createTextNode("}}");
        range.insertNode(endNode);
        range.insertNode(fragment);
        range.insertNode(startNode);
        placeCaretAfter(endNode);
        // Remove empty <li> elements added by extractContents()
        const elementsToCheck = [
            startNode.previousElementSibling,
            startNode.nextElementSibling,
            endNode.previousElementSibling,
            endNode.nextElementSibling,
        ];
        for (const element of elementsToCheck) {
            if (element?.tagName === "LI" && !element?.textContent?.trim()) {
                element.remove();
            }
        }
    }
}
