// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

export function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

// https://developer.mozilla.org/en-US/docs/Web/HTML/Block-level_elements
const BLOCK_TAGS = [
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
    return BLOCK_TAGS.includes(element.tagName);
}

export function elementContainsInlineContent(element: Element): boolean {
    for (const child of element.children) {
        if (elementIsBlock(child) || !elementContainsInlineContent(child)) {
            return false;
        }
    }

    return true;
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
    (currentField: DocumentOrShadowRoot): T | null => {
        const anchor = currentField.getSelection()?.anchorNode;

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
    element: Element
): element is HTMLLIElement & HTMLParamElement =>
    isListItem(element) || isParagraph(element);

export const getListItem = getAnchorParent(isListItem);
export const getParagraph = getAnchorParent(isParagraph);
export const getBlockElement = getAnchorParent(isBlockElement);
