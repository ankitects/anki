// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection } from "./cross-browser";

export function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

/**
 * In the web this is probably equivalent to `nodeIsElement`, but this is
 * convenient to convince Typescript.
 */
export function nodeIsCommonElement(node: Node): node is HTMLElement | SVGElement {
    return node instanceof HTMLElement || node instanceof SVGElement;
}

export function nodeIsText(node: Node): node is Text {
    return node.nodeType === Node.TEXT_NODE;
}

export function nodeIsComment(node: Node): node is Comment {
    return node.nodeType === Node.COMMENT_NODE;
}

// https://developer.mozilla.org/en-US/docs/Web/HTML/Block-level_elements
export const BLOCK_ELEMENTS = [
    "ADDRESS",
    "ARTICLE",
    "ASIDE",
    "BLOCKQUOTE",
    "DETAILS",
    "DIALOG",
    "DD",
    "DIV",
    "DL",
    "DT",
    "FIELDSET",
    "FIGCAPTION",
    "FIGURE",
    "FOOTER",
    "FORM",
    "H1",
    "H2",
    "H3",
    "H4",
    "H5",
    "H6",
    "HEADER",
    "HGROUP",
    "HR",
    "LI",
    "MAIN",
    "NAV",
    "OL",
    "P",
    "PRE",
    "SECTION",
    "TABLE",
    "UL",
];

export function hasBlockAttribute(element: Element): boolean {
    return element.hasAttribute("block") && element.getAttribute("block") !== "false";
}

export function elementIsBlock(element: Element): boolean {
    return BLOCK_ELEMENTS.includes(element.tagName) || hasBlockAttribute(element);
}

export const NO_SPLIT_TAGS = ["RUBY"];

export function elementShouldNotBeSplit(element: Element): boolean {
    return elementIsBlock(element) || NO_SPLIT_TAGS.includes(element.tagName);
}

// https://developer.mozilla.org/en-US/docs/Glossary/Empty_element
export const EMPTY_ELEMENTS = [
    "AREA",
    "BASE",
    "BR",
    "COL",
    "EMBED",
    "HR",
    "IMG",
    "INPUT",
    "LINK",
    "META",
    "PARAM",
    "SOURCE",
    "TRACK",
    "WBR",
];

export function elementIsEmpty(element: Element): boolean {
    return EMPTY_ELEMENTS.includes(element.tagName);
}

export function nodeContainsInlineContent(node: Node): boolean {
    for (const child of node.childNodes) {
        if (
            (nodeIsElement(child) && elementIsBlock(child))
            || !nodeContainsInlineContent(child)
        ) {
            return false;
        }
    }

    // empty node is trivially inline
    return true;
}

/**
 * Consumes the input fragment.
 */
export function fragmentToString(fragment: DocumentFragment): string {
    const fragmentDiv = document.createElement("div");
    fragmentDiv.appendChild(fragment);
    const html = fragmentDiv.innerHTML;

    return html;
}

const getAnchorParent =
    <T extends Element>(predicate: (element: Element) => element is T) => (root: Node): T | null => {
        const anchor = getSelection(root)?.anchorNode;

        if (!anchor) {
            return null;
        }

        let anchorParent: T | null = null;
        let element = nodeIsElement(anchor) ? anchor : anchor.parentElement;

        while (element) {
            anchorParent = anchorParent || (predicate(element) ? element : null);
            element = element.parentElement;
        }

        return anchorParent;
    };

const isListItem = (element: Element): element is HTMLLIElement =>
    window.getComputedStyle(element).display === "list-item";

export const getListItem = getAnchorParent(isListItem);
