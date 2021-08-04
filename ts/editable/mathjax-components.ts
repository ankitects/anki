import Mathjax_svelte from "./Mathjax.svelte";

class MathjaxInline extends HTMLElement {
    connectedCallback() {
        this.decorate();
    }

    decorate(): void {
        this.contentEditable = "false";

        const mathjax = (this.dataset.mathjax = this.innerText);
        const type = "inline";
        this.innerHTML = "";

        new Mathjax_svelte({
            target: this,
            props: { mathjax, type },
        });
    }

    undecorate(): void {
        this.removeAttribute("contentEditable");
        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;
    }
}

customElements.define("anki-mathjax-inline", MathjaxInline);

class MathjaxBlock extends HTMLElement {
    connectedCallback() {
        this.decorate();
    }

    decorate(): void {
        this.contentEditable = "false";

        const mathjax = (this.dataset.mathjax = this.innerText);
        const type = "block";
        this.innerHTML = "";

        new Mathjax_svelte({
            target: this,
            props: { mathjax, type },
        });
    }

    undecorate(): void {
        this.removeAttribute("contentEditable");
        this.innerHTML = this.dataset.mathjax ?? "";
        delete this.dataset.mathjax;
    }
}

customElements.define("anki-mathjax-block", MathjaxBlock);

// TODO mathjax regex will prob. fail at double quotes
const mathjaxInlineTagPattern =
    /<anki-mathjax-inline[^>]*?data-mathjax="(.*?)"[^>]*?>.*?<\/anki-mathjax-inline>/gsu;
const mathjaxBlockTagPattern =
    /<anki-mathjax-block[^>]*?data-mathjax="(.*?)"[^>]*?>.*?<\/anki-mathjax-block>/gsu;

export function toMathjaxDelimiters(html: string): string {
    return html
        .replace(
            mathjaxInlineTagPattern,
            (_match: string, text: string) => `\\(${text}\\)`
        )
        .replace(
            mathjaxBlockTagPattern,
            (_match: string, text: string) => `\\[${text}\\]`
        );
}

const mathjaxInlineDelimiterPattern = /\\\((.*?)\\\)/gsu;
const mathjaxBlockDelimiterPattern = /\\\[(.*?)\\\]/gsu;

export function toMathjaxTags(html: string): string {
    return html
        .replace(
            mathjaxInlineDelimiterPattern,
            (_match: string, text: string) =>
                `<anki-mathjax-inline>${text}</anki-mathjax-inline>`
        )
        .replace(
            mathjaxBlockDelimiterPattern,
            (_match: string, text: string) =>
                `<anki-mathjax-block>${text}</anki-mathjax-block>`
        );
}
