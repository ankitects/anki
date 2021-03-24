import { filterSpan } from "./htmlFilterSpan";

interface TagsAllowed {
    [tagName: string]: FilterMethod;
}

type FilterMethod = (element: Element) => void;

function doNothing() {}

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

function blockExcept(attrs: string[]): FilterMethod {
    return (element: Element) =>
        filterOutAttributes(
            (attributeName: string) => !attrs.includes(attributeName),
            element
        );
}


function removeElement(element: Element): void {
    element.parentNode?.removeChild(element);
}

function unwrapElement(element: Element): void {
    element.outerHTML = element.innerHTML;
}

const tagsAllowedBasic: TagsAllowed = {
    ANKITOP: doNothing,
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

export function filterElement(element: Element, extendedMode: boolean): void {
    const tagName = element.tagName;
    const tagsAllowed = extendedMode ? tagsAllowedExtended : tagsAllowedBasic;

    if (tagsAllowed.hasOwnProperty(tagName)) {
        tagsAllowed[tagName](element);
    }
    else if (element.innerHTML) {
        removeElement(element);
    }
    else {
        unwrapElement(element);
    }
}
