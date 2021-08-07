import "mathjax/es5/tex-svg-full";

import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { decoratedComponents } from "./decorated";
import { nodeIsElement } from "lib/dom";

import Mathjax_svelte from "./Mathjax.svelte";

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

    return referenceNode;
}

function placeCaretAfter(node: Node): void {
    const range = document.createRange();
    range.setStartAfter(node);
    range.collapse(false);

    const selection = document.getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);
}

function moveNodesInsertedOutside(element: Element, allowedChild: Node): () => void {
    const observer = new MutationObserver(() => {
        const childNodes = [...element.childNodes];
        const allowedIndex = childNodes.findIndex((child) => child === allowedChild);

        const beforeChildren = childNodes.slice(0, allowedIndex);
        const afterChildren = childNodes.slice(allowedIndex + 1);

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

    constructor() {
        super();
    }

    static get observedAttributes(): string[] {
        return ["block", "data-mathjax"];
    }

    connectedCallback(): void {
        this.decorate();
        // TODO text is assigned white-space: normal
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

        this.component = new Mathjax_svelte({
            target: this,
            props: { mathjax, block: this.block },
        });
    }

    undecorate(): void {
        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;

        this.component?.$destroy();
        this.component = undefined;

        if (this.block) {
            this.setAttribute("block", "true");
        } else {
            this.removeAttribute("block");
        }
    }
};

decoratedComponents.push(Mathjax);
