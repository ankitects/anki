import "mathjax/es5/tex-svg-full";
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

class Mathjax extends HTMLElement {
    block: boolean = false;
    disconnect: () => void = () => {};

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
}

customElements.define("anki-mathjax", Mathjax);

const mathjaxTagPattern =
    /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

export function toMathjaxDelimiters(html: string): string {
    return html.replace(
        mathjaxTagPattern,
        (_match: string, block: string | undefined, text: string) => {
            console.log("delim", _match, block, "text", text);
            return typeof block === "string" && block !== "false"
                ? `\\[${text}\\]`
                : `\\(${text}\\)`;
        }
    );
}

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

export function toMathjaxTags(html: string): string {
    return html
        .replace(
            mathjaxBlockDelimiterPattern,
            (_match: string, text: string) => (
                console.log(text), `<anki-mathjax block="true">${text}</anki-mathjax>`
            )
        )
        .replace(
            mathjaxInlineDelimiterPattern,
            (_match: string, text: string) => (
                console.log(text), `<anki-mathjax>${text}</anki-mathjax>`
            )
        );
}
