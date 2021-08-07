// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { mathIcon } from "./icons";

const parser = new DOMParser();

function getStyle(nightMode: boolean, fontSize: number): HTMLStyleElement {
    const style = document.createElement("style") as HTMLStyleElement;
    const color = nightMode ? "white" : "black";

    /* color is set for Maths, fill for the empty icon */
    const css = `svg { color: ${color}; fill: ${color}; font-size: ${fontSize}px; }`;
    style.appendChild(document.createTextNode(css));

    return style;
}

function getEmptyIcon(nightMode: boolean, fontSize: number): [string, string] {
    const style = getStyle(nightMode, fontSize);

    const icon = parser.parseFromString(mathIcon, "image/svg+xml");
    const svg = icon.children[0];
    svg.insertBefore(style, svg.children[0]);

    return [svg.outerHTML, ""];
}

export function convertMathjax(
    input: string,
    nightMode: boolean,
    fontSize: number
): [string, string] {
    if (input.trim().length === 0) {
        return getEmptyIcon(nightMode, fontSize);
    }

    const output = globalThis.MathJax.tex2svg(input);
    const svg = output.children[0];

    if (svg.viewBox.baseVal.height === 16) {
        return getEmptyIcon(nightMode, fontSize);
    }

    let title = "";

    if (svg.innerHTML.includes("data-mjx-error")) {
        svg.querySelector("rect").setAttribute("fill", "yellow");
        svg.querySelector("text").setAttribute("color", "red");
        title = svg.querySelector("title").innerHTML;
    } else {
        const style = getStyle(nightMode, fontSize);
        svg.insertBefore(style, svg.children[0]);
    }

    return [svg.outerHTML, title];
}
