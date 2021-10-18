// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

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

export function convertMathjax(
    input: string,
    nightMode: boolean,
    fontSize: number,
): [string, string] {
    const style = getStyle(getCSS(nightMode, fontSize));

    if (input.trim().length === 0) {
        return getEmptyIcon(style);
    }

    let output: Element;
    try {
        output = globalThis.MathJax.tex2svg(input);
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
