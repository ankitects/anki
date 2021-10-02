// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

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

export function wrapInternal(
    root: DocumentOrShadowRoot,
    front: string,
    back: string,
    plainText: boolean
): void {
    const selection = root.getSelection()!;
    const range = selection.getRangeAt(0);
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
        !span.innerHTML &&
        /* ugly solution: treat <anki-mathjax> differently than other wraps */ !front.includes(
            "<anki-mathjax"
        )
    ) {
        moveCursorPastPostfix(selection, back);
    }
}
