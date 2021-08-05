// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { DecoratedElement } from "./decorated";
import { decoratedComponents } from "./decorated";
import { bridgeCommand } from "lib/bridgecommand";
import { elementIsBlock, getBlockElement } from "lib/dom";
// import { inCodable } from "./toolbar";
// import { wrap } from "./wrap";

export function caretToEnd(node: Node): void {
    const range = document.createRange();
    range.selectNodeContents(node);
    range.collapse(false);
    const selection = (node.getRootNode() as Document | ShadowRoot).getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);
}

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
        const clone = this.cloneNode(true) as Element;

        for (const component of decoratedComponents) {
            for (const element of clone.getElementsByTagName(component.tagName)) {
                (element as DecoratedElement).undecorate();
            }
        }

        const result =
            containsInlineContent(clone) && clone.innerHTML.endsWith("<br>")
                ? clone.innerHTML.slice(0, -4) // trim trailing <br>
                : clone.innerHTML;

        return result;
    }

    connectedCallback(): void {
        this.setAttribute("contenteditable", "");
    }

    focus(): void {
        super.focus();
        // TODO
        // inCodable.set(false);
    }

    caretToEnd(): void {
        caretToEnd(this);
    }

    surroundSelection(before: string, after: string): void {
        // TODO
        // wrap(before, after);
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

customElements.define("anki-editable", Editable);
