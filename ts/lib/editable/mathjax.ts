// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import mathIcon from "@mdi/svg/svg/math-integral-box.svg?src";

/**
 * MathJax is the single largest contributor to the editor bundle, and most
 * notes contain no MathJax at all. Fetch it the first time something actually
 * needs typesetting instead of on editor startup.
 *
 * This injects a script tag rather than using a dynamic import, because the
 * editor is bundled by esbuild with a single outfile and no code splitting -
 * an import() would simply be inlined back into editor.js. It mirrors what
 * _lazyLoadMathJax() in ts/reviewer/index.ts does, except the editor needs the
 * SVG output rather than CHTML.
 *
 * The window.MathJax configuration object is still set up eagerly by
 * js/mathjax.js, so it is always in place before this runs.
 */
const mathjaxUrl = "/_anki/js/vendor/mathjax/tex-svg-full.js";

let mathjaxPromise: Promise<void> | null = null;

function loadMathjax(): Promise<void> {
    return (mathjaxPromise ??= new Promise<void>((resolve, reject) => {
        const script = document.createElement("script");
        script.src = mathjaxUrl;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Failed to load MathJax"));
        document.head.appendChild(script);
    }).then(() => globalThis.MathJax?.startup?.promise));
}

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

/**
 * Rendered synchronously while MathJax is still loading. This is the same icon
 * an empty anki-mathjax element shows, and is only visible for the first
 * element typeset in a session.
 */
export function emptyIcon(nightMode: boolean, fontSize: number): [string, string] {
    return getEmptyIcon(getStyle(getCSS(nightMode, fontSize)));
}

export async function convertMathjax(
    input: string,
    nightMode: boolean,
    fontSize: number,
): Promise<[string, string]> {
    input = revealClozeAnswers(input);
    const style = getStyle(getCSS(nightMode, fontSize));

    if (input.trim().length === 0) {
        return getEmptyIcon(style);
    }

    try {
        await loadMathjax();
    } catch (e) {
        return ["MathJax Error", String(e)];
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
