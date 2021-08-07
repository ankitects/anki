// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { mathIcon } from "./icons";

const parser = new DOMParser();

function getStyle(): HTMLStyleElement {
    const style = document.createElement("style") as HTMLStyleElement;
    /* color is set for Maths, fill for the empty icon */
    const css = `svg { color: white; fill: white; font-size: 20px; }`;
    style.appendChild(document.createTextNode(css));

    return style;
}

function getEmptyIcon(): [string, string] {
    const style = getStyle();

    const icon = parser.parseFromString(mathIcon, "image/svg+xml");
    const svg = icon.children[0];
    svg.insertBefore(style, svg.children[0]);

    return [svg.outerHTML, ""];
}

export function convertMathjax(input: string): [string, string] {
    if (input.trim().length === 0) {
        return getEmptyIcon();
    }

    const output = globalThis.MathJax.tex2svg(input);
    const svg = output.children[0];

    if (svg.viewBox.baseVal.height === 16) {
        return getEmptyIcon();
    }

    let title = "";

    if (svg.innerHTML.includes("data-mjx-error")) {
        svg.querySelector("rect").setAttribute("fill", "yellow");
        svg.querySelector("text").setAttribute("color", "red");
        title = svg.querySelector("title").innerHTML;
    } else {
        const style = getStyle();
        svg.insertBefore(style, svg.children[0]);
    }

    return [svg.outerHTML, title];
}
