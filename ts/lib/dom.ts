// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

export function nodeIsText(node: Node): node is Text {
    return node.nodeType === Node.TEXT_NODE;
}

// https://developer.mozilla.org/en-US/docs/Web/HTML/Block-level_elements
const BLOCK_ELEMENTS = [
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

export function elementIsBlock(element: Element): boolean {
    return BLOCK_ELEMENTS.includes(element.tagName);
}

// https://developer.mozilla.org/en-US/docs/Glossary/Empty_element
const EMPTY_ELEMENTS = [
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
            (nodeIsElement(child) && elementIsBlock(child)) ||
            !nodeContainsInlineContent(child)
        ) {
            return false;
        }
    }

    // empty node is trivially inline
    return true;
}

export function fragmentToString(fragment: DocumentFragment): string {
    const fragmentDiv = document.createElement("div");
    fragmentDiv.appendChild(fragment);
    const html = fragmentDiv.innerHTML;

    return html;
}

export const NO_SPLIT_TAGS = ["RUBY"];

export function elementShouldNotBeSplit(element: Element): boolean {
    return elementIsBlock(element) || NO_SPLIT_TAGS.includes(element.tagName);
}

export function caretToEnd(node: Node): void {
    const range = new Range();
    range.selectNodeContents(node);
    range.collapse(false);
    const selection = (node.getRootNode() as Document | ShadowRoot).getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);
}

const getAnchorParent =
    <T extends Element>(predicate: (element: Element) => element is T) =>
    (root: DocumentOrShadowRoot): T | null => {
        const anchor = root.getSelection()?.anchorNode;

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
const isParagraph = (element: Element): element is HTMLParamElement =>
    element.tagName === "P";
const isBlockElement = (
    element: Element,
): element is HTMLLIElement & HTMLParamElement =>
    isListItem(element) || isParagraph(element);

export const getListItem = getAnchorParent(isListItem);
export const getParagraph = getAnchorParent(isParagraph);
export const getBlockElement = getAnchorParent(isBlockElement);
