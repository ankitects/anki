import MathjaxBlock_svelte from "./MathjaxBlock.svelte";

class MathjaxBlock extends HTMLElement {
    connectedCallback() {
        this.contentEditable = "false";

        const mathjax = (this.dataset.mathjax = this.innerText);
        this.innerHTML = "";

        new MathjaxBlock_svelte({
            target: this,
            props: { mathjax },
        });
    }
}

// customElements.define("anki-mathjax-inline", MathjaxInline);
customElements.define("anki-mathjax-block", MathjaxBlock);

const mathjaxInlineTagPattern =
    /<anki-mathjax-inline.*?>(.*?)<\/anki-mathjax-inline>/gsu;
const mathjaxBlockTagPattern = /<anki-mathjax-block.*?>(.*?)<\/anki-mathjax-block>/gsu;

export function toMathjaxDelimiters(html: string): string {
    return html
        .replace(
            mathjaxInlineTagPattern,
            (_match: string, text: string) => `\(${text}\)`
        )
        .replace(
            mathjaxBlockTagPattern,
            (_match: string, text: string) => `\[${text}\]`
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
