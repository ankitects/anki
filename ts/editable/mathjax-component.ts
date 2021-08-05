import "mathjax/es5/tex-svg-full";

import Mathjax_svelte from "./Mathjax.svelte";

class Mathjax extends HTMLElement {
    block: boolean = false;

    static get observedAttributes(): string[] {
        return ["block"];
    }

    connectedCallback(): void {
        this.decorate();
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

// TODO mathjax regex will prob. fail at double quotes
const mathjaxTagPattern =
    /<anki-mathjax(?:[^>]*?block="(.*?)")?[^>]*?>(.*?)<\/anki-mathjax>/gsu;

export function toMathjaxDelimiters(html: string): string {
    return html.replace(
        mathjaxTagPattern,
        (_match: string, block: string | undefined, text: string) =>
            typeof block === "string" && block !== "false"
                ? `\\[${text}\\]`
                : `\\(${text}\\)`
    );
}

const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;
const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;

export function toMathjaxTags(html: string): string {
    return html
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
