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

    // Expand the range to include parent nodes whose children are already included.
    // This is intended to prevent the cloze from partially covering elements, which can cause display issues.
    let startParent: Node | null = range.startContainer.parentNode;
    if (
        startParent !== base
        && startParent?.firstChild === range.startContainer && range.startOffset === 0
    ) {
        range.setStartBefore(startParent);
    }
    let endParent: Node | null = range.endContainer.parentNode;
    if (
        endParent !== base
        && endParent?.lastChild === range.endContainer && (
            (range.endContainer.nodeType != Node.ELEMENT_NODE
                && range.endOffset === range.endContainer.textContent?.length)
            || (range.endContainer.nodeType === Node.ELEMENT_NODE
                && range.endOffset === range.endContainer.childNodes.length)
        )
    ) {
        range.setEndAfter(endParent);
    }
    let expand = false;
    do {
        if (
            startParent
            && startParent.parentNode !== base && startParent.parentNode?.firstChild === startParent
            && range.isPointInRange(startParent.parentNode, startParent.parentNode?.childNodes.length)
        ) {
            startParent = startParent.parentNode;
            range.setStartBefore(startParent);
            expand = true;
        } else {
            expand = false;
        }
        if (
            endParent && endParent.parentNode !== base && endParent.parentNode?.lastChild === endParent
            && range.isPointInRange(endParent.parentNode, 0)
        ) {
            endParent = endParent.parentNode;
            range.setEndAfter(endParent);
            expand = true;
        } else if (!expand) {
            expand = false;
        }

        if (range.endOffset === 0) {
            range.setEndBefore(range.endContainer);
            expand = true;
        } else if (!expand) {
            expand = false;
        }
    } while (expand);

    const fragment = range.cloneContents()!;
    const isEmpty = fragment.childNodes.length === 0;
    const startNode = document.createTextNode(`{{c${n}::`);
    const endNode = document.createTextNode("}}");
    range.insertNode(startNode);
    const range2 = range.cloneRange();
    range2.collapse(false);
    range2.insertNode(endNode);

    let referenceNode: Node;
    if (isEmpty) {
        referenceNode = startNode;
    } else {
        referenceNode = endNode;
    }
    placeCaretAfter(referenceNode);
}
