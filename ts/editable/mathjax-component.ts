import "mathjax/es5/tex-svg-full";

import type { DecoratedElement, DecoratedElementConstructor } from "./decorated";
import { decoratedComponents } from "./decorated";
import { nodeIsElement } from "lib/dom";

import Mathjax_svelte from "./Mathjax.svelte";

function moveNodesInsertedBeforeEndToAfterEnd(element: Element): () => void {
    const allowedChildNodes = element.childNodes.length;

    const observer = new MutationObserver(() => {
        for (const node of [...element.childNodes].slice(allowedChildNodes)) {
            element.removeChild(node);

            let referenceNode: Node;

            if (nodeIsElement(node)) {
                referenceNode = element.insertAdjacentElement("afterend", node)!;
            } else {
                element.insertAdjacentText("afterend", (node as Text).wholeText);
                referenceNode = element.nextSibling!;
            }

            if (!referenceNode) {
                continue;
            }

            const range = document.createRange();
            range.setStartAfter(referenceNode);
            range.collapse(false);

            const selection = document.getSelection()!;
            selection.removeAllRanges();
            selection.addRange(range);
        }
    });

    observer.observe(element, { characterData: true, subtree: true });
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
    disconnect: () => void = () => {/* noop */};

    constructor() {
        super();
    }

    static get observedAttributes(): string[] {
        return ["block"];
    }

    connectedCallback(): void {
        this.decorate();
        this.disconnect = moveNodesInsertedBeforeEndToAfterEnd(this);
    }

    disconnectedCallback(): void {
        this.disconnect();
    }

    attributeChangedCallback(_name: string, _old: string, newValue: string): void {
        this.block = newValue !== "false";
    }

    decorate(): void {
        const mathjax = (this.dataset.mathjax = this.innerText);
        this.innerHTML = "";

        new Mathjax_svelte({
            target: this,
            props: { mathjax, block: this.block },
        });
    }

    undecorate(): void {
        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;

        if (this.block) {
            this.setAttribute("block", "true");
        } else {
            this.removeAttribute("block");
        }
    }
};

decoratedComponents.push(Mathjax);
