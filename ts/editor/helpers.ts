// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

export function nodeIsElement(node: Node): node is Element {
    return node.nodeType === Node.ELEMENT_NODE;
}

const INLINE_TAGS = [
    "A",
    "ABBR",
    "ACRONYM",
    "AUDIO",
    "B",
    "BDI",
    "BDO",
    "BIG",
    "BR",
    "BUTTON",
    "CANVAS",
    "CITE",
    "CODE",
    "DATA",
    "DATALIST",
    "DEL",
    "DFN",
    "EM",
    "EMBED",
    "I",
    "IFRAME",
    "IMG",
    "INPUT",
    "INS",
    "KBD",
    "LABEL",
    "MAP",
    "MARK",
    "METER",
    "NOSCRIPT",
    "OBJECT",
    "OUTPUT",
    "PICTURE",
    "PROGRESS",
    "Q",
    "RUBY",
    "S",
    "SAMP",
    "SCRIPT",
    "SELECT",
    "SLOT",
    "SMALL",
    "SPAN",
    "STRONG",
    "SUB",
    "SUP",
    "SVG",
    "TEMPLATE",
    "TEXTAREA",
    "TIME",
    "U",
    "TT",
    "VAR",
    "VIDEO",
    "WBR",
];

export function nodeIsInline(node: Node): boolean {
    return !nodeIsElement(node) || INLINE_TAGS.includes(node.tagName);
}

export function caretToEnd(node: Node): void {
    const range = document.createRange();
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

export function appendInParentheses(text: string, appendix: string): string {
    return `${text} (${appendix})`;
}
