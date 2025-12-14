// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "mathjax/tex-svg.js";

import mathIcon from "@mdi/svg/svg/math-integral-box.svg?src";

const parser = new DOMParser();

function getCSS(nightMode: boolean, fontSize: number): string {
    const color = nightMode ? "white" : "black";
    /* color is set for Maths, fill for the empty icon */
    return `svg { color: ${color}; fill: ${color}; font-size: ${fontSize}px; };`;
}

function getStyle(css: string): HTMLStyleElement {
    const style = document.createElement("style");
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
    input = revealClozeAnswers(input);
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

/**
 * Escape characters which are technically legal in Mathjax, but confuse HTML.
 */
export function escapeSomeEntities(value: string): string {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export function unescapeSomeEntities(value: string): string {
    return value.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");
}

function revealClozeAnswers(input: string): string {
    // one-line version of regex in cloze.rs
    const regex = /\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}/gis;
    return input.replace(regex, "[$2]");
}
