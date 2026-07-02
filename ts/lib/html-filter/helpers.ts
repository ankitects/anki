// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function isHTMLElement(elem: Element): elem is HTMLElement {
    return elem instanceof HTMLElement;
}

export function isNightMode(): boolean {
    return document.body.classList.contains("nightMode");
}
