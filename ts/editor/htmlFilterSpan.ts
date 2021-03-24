import { isNightMode } from "./helpers";

/* keys are allowed properties, values are blocked values */
const stylingAllowListNightMode = {
    "font-weight": [],
    "font-style": [],
    "text-decoration-line": [],
};

const stylingAllowList = {
    color: [],
    "background-color": ["transparent"],
    ...stylingAllowListNightMode,
};

function isStylingAllowed(property: string, value: string): boolean {
    const allowList = isNightMode() ? stylingAllowListNightMode : stylingAllowList;

    return allowList.hasOwnProperty(property) && !allowList[property].includes(value);
}

const allowedAttrs = ["STYLE"];

export function filterSpan(element: Element): void {
    // filter out attributes
    for (const attr of [...element.attributes]) {
        const attrName = attr.name.toUpperCase();

        if (!allowedAttrs.includes(attrName)) {
            element.removeAttributeNode(attr);
        }
    }

    // filter styling
    const elementStyle = (element as HTMLSpanElement).style;

    for (const property of [...elementStyle]) {
        const value = elementStyle.getPropertyValue(name);

        if (!isStylingAllowed(property, value)) {
            elementStyle.removeProperty(property);
        }
    }
}
