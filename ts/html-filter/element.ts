import { isHTMLElement, isNightMode } from "./helpers";
import { removeNode as removeElement } from "./node";
import {
    filterStylingNightMode,
    filterStylingLightMode,
    filterStylingInternal,
} from "./styling";

interface TagsAllowed {
    [tagName: string]: FilterMethod;
}

type FilterMethod = (element: Element) => void;

function filterOutAttributes(
    attributePredicate: (attributeName: string) => boolean,
    element: Element
): void {
    for (const attr of [...element.attributes]) {
        const attrName = attr.name.toUpperCase();

        if (attributePredicate(attrName)) {
            element.removeAttributeNode(attr);
        }
    }
}

function blockAll(element: Element): void {
    filterOutAttributes(() => true, element);
}

const blockExcept = (attrs: string[]): FilterMethod => (element: Element): void =>
    filterOutAttributes(
        (attributeName: string) => !attrs.includes(attributeName),
        element
    );

function unwrapElement(element: Element): void {
    element.outerHTML = element.innerHTML;
}

function filterSpan(element: Element): void {
    const filterAttrs = blockExcept(["STYLE"]);
    filterAttrs(element);

    const filterStyle = isNightMode() ? filterStylingNightMode : filterStylingLightMode;
    filterStyle(element as HTMLSpanElement);
}

const tagsAllowedBasic: TagsAllowed = {
    BR: blockAll,
    IMG: blockExcept(["SRC"]),
    DIV: blockAll,
    P: blockAll,
    SUB: blockAll,
    SUP: blockAll,
    TITLE: removeElement,
};

const tagsAllowedExtended: TagsAllowed = {
    ...tagsAllowedBasic,
    A: blockExcept(["HREF"]),
    B: blockAll,
    BLOCKQUOTE: blockAll,
    CODE: blockAll,
    DD: blockAll,
    DL: blockAll,
    DT: blockAll,
    EM: blockAll,
    FONT: blockExcept(["COLOR"]),
    H1: blockAll,
    H2: blockAll,
    H3: blockAll,
    I: blockAll,
    LI: blockAll,
    OL: blockAll,
    PRE: blockAll,
    RP: blockAll,
    RT: blockAll,
    RUBY: blockAll,
    SPAN: filterSpan,
    STRONG: blockAll,
    TABLE: blockAll,
    TD: blockExcept(["COLSPAN", "ROWSPAN"]),
    TH: blockExcept(["COLSPAN", "ROWSPAN"]),
    TR: blockExcept(["ROWSPAN"]),
    U: blockAll,
    UL: blockAll,
};

const filterElementTagsAllowed = (tagsAllowed: TagsAllowed) => (
    element: Element
): void => {
    const tagName = element.tagName;

    if (Object.prototype.hasOwnProperty.call(tagsAllowed, tagName)) {
        tagsAllowed[tagName](element);
    } else if (element.innerHTML) {
        removeElement(element);
    } else {
        unwrapElement(element);
    }
};

export const filterElementBasic = filterElementTagsAllowed(tagsAllowedBasic);
export const filterElementExtended = filterElementTagsAllowed(tagsAllowedExtended);

export function filterElementInternal(element: Element): void {
    if (isHTMLElement(element)) {
        filterStylingInternal(element);
    }
}
