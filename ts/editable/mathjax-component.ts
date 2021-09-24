// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
@typescript-eslint/no-explicit-any: "off",
 */

import "mathjax/es5/tex-svg-full";

import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { nodeIsElement } from "lib/dom";
import { nightModeKey } from "components/context-keys";

import Mathjax_svelte from "./Mathjax.svelte";

const customInputEvent = new Event("custominput", { bubbles: true });

function moveNodeOutOfElement(
    element: Element,
    node: Node,
    placement: "beforebegin" | "afterend"
): Node {
    element.removeChild(node);

    let referenceNode: Node;

    if (nodeIsElement(node)) {
        referenceNode = element.insertAdjacentElement(placement, node)!;
    } else {
        element.insertAdjacentText(placement, (node as Text).wholeText);
        referenceNode =
            placement === "beforebegin"
                ? element.previousSibling!
                : element.nextSibling!;
    }

    element.dispatchEvent(customInputEvent);

    return referenceNode;
}

function placeCaretAfter(node: Node): void {
    const range = new Range();
    range.setStartAfter(node);
    range.collapse(false);

    const selection = document.getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);
}

function moveNodesInsertedOutside(element: Element, allowedChild: Node): () => void {
    const observer = new MutationObserver(() => {
        if (element.childNodes.length === 1) {
            return;
        }

        const childNodes = [...element.childNodes];
        const allowedIndex = childNodes.findIndex((child) => child === allowedChild);

        const beforeChildren = childNodes.slice(0, allowedIndex);
        const afterChildren = childNodes.slice(allowedIndex + 1);

        // Special treatment for pressing return after mathjax block
        if (
            afterChildren.length === 2 &&
            afterChildren.every((child) => (child as Element).tagName === "BR")
        ) {
            const first = afterChildren.pop();
            element.removeChild(first!);
        }

        let lastNode: Node | null = null;

        for (const node of beforeChildren) {
            lastNode = moveNodeOutOfElement(element, node, "beforebegin");
        }

        for (const node of afterChildren) {
            lastNode = moveNodeOutOfElement(element, node, "afterend");
        }

        if (lastNode) {
            placeCaretAfter(lastNode);
        }
    });

    observer.observe(element, { childList: true, characterData: true });
    return () => observer.disconnect();
}

const mathjaxTagPattern =
    /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

export const Mathjax: DecoratedElementConstructor = class Mathjax
    extends HTMLElement
    implements DecoratedElement
{
    static tagName = "anki-mathjax";

    static toStored(undecorated: string): string {
        return undecorated.replace(
            mathjaxTagPattern,
            (_match: string, block: string | undefined, text: string) => {
                return typeof block === "string" && block !== "false"
                    ? `\\[${text}\\]`
                    : `\\(${text}\\)`;
            }
        );
    }

    static toUndecorated(stored: string): string {
        return stored
            .replace(
                mathjaxBlockDelimiterPattern,
                (_match: string, text: string) =>
                    `<anki-mathjax block="true">${text}</anki-mathjax>`
            )
            .replace(
                mathjaxInlineDelimiterPattern,
                (_match: string, text: string) => `<anki-mathjax>${text}</anki-mathjax>`
            );
    }

    block = false;
    disconnect: () => void = () => {
        /* noop */
    };
    component?: Mathjax_svelte;

    static get observedAttributes(): string[] {
        return ["block", "data-mathjax"];
    }

    connectedCallback(): void {
        this.decorate();
        this.disconnect = moveNodesInsertedOutside(this, this.children[0]);
    }

    disconnectedCallback(): void {
        this.disconnect();
    }

    attributeChangedCallback(name: string, _old: string, newValue: string): void {
        switch (name) {
            case "block":
                this.block = newValue !== "false";
                this.component?.$set({ block: this.block });
                break;
            case "data-mathjax":
                this.component?.$set({ mathjax: newValue });
                break;
        }
    }

    decorate(): void {
        const mathjax = (this.dataset.mathjax = this.innerText);
        this.innerHTML = "";
        this.style.whiteSpace = "normal";

        const context = new Map();
        context.set(
            nightModeKey,
            document.documentElement.classList.contains("night-mode")
        );

        this.component = new Mathjax_svelte({
            target: this,
            props: {
                mathjax,
                block: this.block,
                autofocus: this.hasAttribute("focusonmount"),
            },
            context,
        } as any);
    }

    undecorate(): void {
        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;
        this.removeAttribute("style");
        this.removeAttribute("focusonmount");

        this.component?.$destroy();
        this.component = undefined;

        if (this.block) {
            this.setAttribute("block", "true");
        } else {
            this.removeAttribute("block");
        }
    }
};
