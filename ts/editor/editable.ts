// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Cloze } from "./cloze";
import { bridgeCommand } from "./lib";
import { nodeIsInline, caretToEnd, getBlockElement } from "./helpers";
import { setEditableButtons } from "./toolbar";
import { wrap } from "./wrap";

function containsInlineContent(field: Element): boolean {
    if (field.childNodes.length === 0) {
        // for now, for all practical purposes, empty fields are in block mode
        return false;
    }

    for (const child of field.children) {
        if (!nodeIsInline(child)) {
            return false;
        }
    }

    return true;
}

export class Editable extends HTMLElement {
    set fieldHTML(content: string) {
        this.innerHTML = content;

        if (containsInlineContent(this)) {
            this.appendChild(document.createElement("br"));
        }
    }

    get fieldHTML(): string {
        const clone = this.cloneNode(true) as Element;

        for (const cloze of clone.getElementsByTagName("anki-cloze")) {
            (cloze as Cloze).cleanup();
        }

        return containsInlineContent(clone) && clone.innerHTML.endsWith("<br>")
            ? clone.innerHTML.slice(0, -4) // trim trailing <br>
            : clone.innerHTML;
    }

    connectedCallback(): void {
        this.setAttribute("contenteditable", "");
    }

    focus(): void {
        super.focus();
        setEditableButtons();
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
