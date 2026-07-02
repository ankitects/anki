// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { isHTMLElement, isNightMode } from "./helpers";
import { removeNode as removeElement } from "./node";
import { filterStylingInternal, filterStylingLightMode, filterStylingNightMode } from "./styling";

interface TagsAllowed {
    [tagName: string]: FilterMethod;
}

type FilterMethod = (element: Element) => void;

function filterAttributes(
    attributePredicate: (attributeName: string) => boolean,
    element: Element,
): void {
    for (const attr of [...element.attributes]) {
        const attrName = attr.name.toUpperCase();

        if (!attributePredicate(attrName)) {
            element.removeAttributeNode(attr);
        }
    }
}

function allowNone(element: Element): void {
    filterAttributes(() => false, element);
}

const allow = (attrs: string[]): FilterMethod => (element: Element): void =>
    filterAttributes(
        (attributeName: string) => attrs.includes(attributeName),
        element,
    );

function unwrapElement(element: Element): void {
    element.replaceWith(...element.childNodes);
}

function filterSpan(element: Element): void {
    const filterAttrs = allow(["STYLE"]);
    filterAttrs(element);

    const filterStyle = isNightMode() ? filterStylingNightMode : filterStylingLightMode;
    filterStyle(element as HTMLSpanElement);
}

const tagsAllowedBasic: TagsAllowed = {
    BR: allowNone,
    IMG: allow(["SRC", "ALT"]),
    DIV: allowNone,
    P: allowNone,
    SUB: allowNone,
    SUP: allowNone,
    TITLE: removeElement,
};

const tagsAllowedExtended: TagsAllowed = {
    ...tagsAllowedBasic,
    A: allow(["HREF"]),
    B: allowNone,
    BLOCKQUOTE: allowNone,
    CODE: allowNone,
    DD: allowNone,
    DL: allowNone,
    DT: allowNone,
    EM: allowNone,
    FONT: allow(["COLOR"]),
    H1: allowNone,
    H2: allowNone,
    H3: allowNone,
    I: allowNone,
    LI: allowNone,
    OL: allowNone,
    PRE: allowNone,
    RP: allowNone,
    RT: allowNone,
    RUBY: allowNone,
    SPAN: filterSpan,
    STRONG: allowNone,
    TABLE: allowNone,
    TD: allow(["COLSPAN", "ROWSPAN"]),
    TH: allow(["COLSPAN", "ROWSPAN"]),
    TR: allow(["ROWSPAN"]),
    U: allowNone,
    UL: allowNone,
};

const filterElementTagsAllowed = (tagsAllowed: TagsAllowed) => (element: Element): void => {
    const tagName = element.tagName;

    if (Object.prototype.hasOwnProperty.call(tagsAllowed, tagName)) {
        tagsAllowed[tagName](element);
    } else if (element.innerHTML) {
        unwrapElement(element);
    } else {
        removeElement(element);
    }
};

export const filterElementBasic = filterElementTagsAllowed(tagsAllowedBasic);
export const filterElementExtended = filterElementTagsAllowed(tagsAllowedExtended);

export function filterElementInternal(element: Element): void {
    if (isHTMLElement(element)) {
        filterStylingInternal(element);
    }
}
