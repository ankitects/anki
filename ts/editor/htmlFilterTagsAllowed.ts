import { filterSpan } from "./htmlFilterSpan";

type FilterMethod = (element: Element) => void;

interface TagsAllowed {
    [key: string]: FilterMethod;
}

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

function blockExcept(attrs: string[]): FilterMethod {
    return (element: Element) =>
        filterOutAttributes(
            (attributeName: string) => !attrs.includes(attributeName),
            element
        );
}

function blockAll(element: Element): void {
    filterOutAttributes(() => true, element);
}

export const tagsAllowedBasic: TagsAllowed = {
    BR: blockAll,
    IMG: blockExcept(["SRC"]),
    DIV: blockAll,
    P: blockAll,
    SUB: blockAll,
    SUP: blockAll,
};

export const tagsAllowedExtended: TagsAllowed = {
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
