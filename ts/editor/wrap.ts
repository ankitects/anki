import { getCurrentField, setFormat } from ".";

function wrappedExceptForWhitespace(text: string, front: string, back: string): string {
    const match = text.match(/^(\s*)([^]*?)(\s*)$/)!;
    return match[1] + front + match[2] + back + match[3];
}

function wrapInternal(front: string, back: string, plainText: boolean): void {
    const currentField = getCurrentField()!;
    const s = currentField.getSelection();
    let r = s.getRangeAt(0);
    const content = r.cloneContents();
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
        // run with an empty selection; move cursor back past postfix
        r = s.getRangeAt(0);
        r.setStart(r.startContainer, r.startOffset - back.length);
        r.collapse(true);
        s.removeAllRanges();
        s.addRange(r);
    }
}

export function wrap(front: string, back: string): void {
    wrapInternal(front, back, false);
}

/* currently unused */
export function wrapIntoText(front: string, back: string): void {
    wrapInternal(front, back, true);
}
