// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { cardRendering, Generic } from "../lib/proto";
import { mathIcon } from "./icons";

const parser = new DOMParser();

function getCSS(nightMode: boolean, fontSize: number): string {
    const color = nightMode ? "white" : "black";
    /* color is set for Maths, fill for the empty icon */
    return `svg { color: ${color}; fill: ${color}; font-size: ${fontSize}px; };`;
}

function getStyle(css: string): HTMLStyleElement {
    const style = document.createElement("style") as HTMLStyleElement;
    style.appendChild(document.createTextNode(css));
    return style;
}

function getEmptyIcon(style: HTMLStyleElement): [string, string] {
    const icon = parser.parseFromString(mathIcon, "image/svg+xml");
    const svg = icon.children[0];
    svg.insertBefore(style, svg.children[0]);

    return [svg.outerHTML, "MathJax"];
}

export async function convertMathjax(
    input: string,
    nightMode: boolean,
    fontSize: number,
): Promise<[string, string]> {
    const style = getStyle(getCSS(nightMode, fontSize));

    if (input.trim().length === 0) {
        return getEmptyIcon(style);
    }

    let output: Element;
    try {
        const strippedInput = await cardRendering.renderClozeForMathjax(
            Generic.String.create({ val: input }),
        );
        output = globalThis.MathJax.tex2svg(strippedInput.val);
    } catch (e) {
        return ["Mathjax Error", String(e)];
    }

    const svg = output.children[0] as SVGElement;

    if ((svg as any).viewBox.baseVal.height === 16) {
        return getEmptyIcon(style);
    }

    let title = "";

    if (svg.innerHTML.includes("data-mjx-error")) {
        svg.querySelector("rect")?.setAttribute("fill", "yellow");
        svg.querySelector("text")?.setAttribute("color", "red");
        title = svg.querySelector("title")?.innerHTML ?? "";
    } else {
        svg.insertBefore(style, svg.children[0]);
    }

    return [svg.outerHTML, title];
}

/**
 * Escape characters which are technically legal in Mathjax, but confuse HTML.
 */
export function escapeSomeEntities(value: string): string {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function unescapeSomeEntities(value: string): string {
    return value.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
}
