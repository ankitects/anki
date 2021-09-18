// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function loadStyleLink(
    href: string,
    node: Node
): [HTMLLinkElement, Promise<void>] {
    const link = document.createElement("link");
    link.setAttribute("rel", "stylesheet");
    link.setAttribute("href", href);

    let linkResolve: () => void;
    const linkPromise = new Promise<void>((resolve) => (linkResolve = resolve));

    link.addEventListener("load", () => linkResolve());
    node.appendChild(link);

    return [link, linkPromise];
}

export function loadStyleTag(node: Node): [HTMLStyleElement, Promise<void>] {
    const style = document.createElement("style");
    style.setAttribute("rel", "stylesheet");

    let styleResolve: () => void;
    const stylePromise = new Promise<void>((resolve) => (styleResolve = resolve));

    style.addEventListener("load", () => styleResolve());
    node.appendChild(style);

    return [style, stylePromise];
}
