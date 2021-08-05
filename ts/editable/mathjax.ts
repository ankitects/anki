// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function convertMathjax(input: string): string {
    const svg = globalThis.MathJax.tex2svg(input).children[0];

    const style = document.createElement("style") as HTMLStyleElement;

    const styles = `svg { color: white; font-size: 24px; }`;
    style.appendChild(document.createTextNode(styles));

    svg.insertBefore(style, svg.children[0]);
    return svg.outerHTML;
}
