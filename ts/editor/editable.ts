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
    containedClozes: Cloze[];

    constructor() {
        super();
        this.containedClozes = [];

        this.onNewCloze = this.onNewCloze.bind(this);
    }

    set fieldHTML(content: string) {
        this.innerHTML = content;

        if (containsInlineContent(this)) {
            this.appendChild(document.createElement("br"));
        }
    }

    get fieldHTML(): string {
        for (const cloze of this.containedClozes) {
            cloze.cleanup();
        }

        const result = containsInlineContent(this) && this.innerHTML.endsWith("<br>")
            ? this.innerHTML.slice(0, -4) // trim trailing <br>
            : this.innerHTML;

        for (const cloze of this.containedClozes) {
            cloze.decorate();
        }

        return result;
    }

    connectedCallback(): void {
        this.setAttribute("contenteditable", "");
        this.addEventListener("newcloze", this.onNewCloze);
    }

    disconnectedCallback(): void {
        this.removeEventListener("newcloze", this.onNewCloze);
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

    onNewCloze(event: Event): void {
        this.containedClozes.push(event.target as Cloze);
    }
}
