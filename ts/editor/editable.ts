// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { bridgeCommand } from "./lib";
import { elementIsBlock, caretToEnd, getBlockElement } from "./helpers";
import { inCodable } from "./toolbar";
import { wrap } from "./wrap";

function containsInlineContent(element: Element): boolean {
    for (const child of element.children) {
        if (elementIsBlock(child) || !containsInlineContent(child)) {
            return false;
        }
    }

    return true;
}

export class Editable extends HTMLElement {
    set fieldHTML(content: string) {
        this.innerHTML = content;

        if (content.length > 0 && containsInlineContent(this)) {
            this.appendChild(document.createElement("br"));
        }
    }

    get fieldHTML(): string {
        return containsInlineContent(this) && this.innerHTML.endsWith("<br>")
            ? this.innerHTML.slice(0, -4) // trim trailing <br>
            : this.innerHTML;
    }

    connectedCallback(): void {
        this.setAttribute("contenteditable", "");
    }

    focus(): void {
        super.focus();
        inCodable.set(false);
    }

    caretToEnd(): void {
        caretToEnd(this);
    }

    surroundSelection(before: string, after: string): void {
        wrap(before, after);
    }

    onEnter(event: KeyboardEvent): void {
        if (
            !getBlockElement(this.getRootNode() as Document | ShadowRoot) !==
            event.shiftKey
        ) {
            event.preventDefault();
            document.execCommand("insertLineBreak");
        }
    }

    onPaste(event: ClipboardEvent): void {
        bridgeCommand("paste");
        event.preventDefault();
    }
}
