/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import { nodeIsElement } from "./helpers";

export function filterHTML(
    html: string,
    internal: boolean,
    extendedMode: boolean
): string {
    // wrap it in <top> as we aren't allowed to change top level elements
    const top = document.createElement("ankitop");
    top.innerHTML = html;

    if (internal) {
        filterInternalNode(top);
    } else {
        filterNode(top, extendedMode);
    }
    let outHtml = top.innerHTML;
    if (!extendedMode && !internal) {
        // collapse whitespace
        outHtml = outHtml.replace(/[\n\t ]+/g, " ");
    }
    outHtml = outHtml.trim();
    return outHtml;
}

let allowedTagsBasic = {};
let allowedTagsExtended = {};

let TAGS_WITHOUT_ATTRS = ["P", "DIV", "BR", "SUB", "SUP"];
for (const tag of TAGS_WITHOUT_ATTRS) {
    allowedTagsBasic[tag] = { attrs: [] };
}

TAGS_WITHOUT_ATTRS = [
    "B",
    "BLOCKQUOTE",
    "CODE",
    "DD",
    "DL",
    "DT",
    "EM",
    "H1",
    "H2",
    "H3",
    "I",
    "LI",
    "OL",
    "PRE",
    "RP",
    "RT",
    "RUBY",
    "STRONG",
    "TABLE",
    "U",
    "UL",
];
for (const tag of TAGS_WITHOUT_ATTRS) {
    allowedTagsExtended[tag] = { attrs: [] };
}

allowedTagsBasic["IMG"] = { attrs: ["SRC"] };

allowedTagsExtended["A"] = { attrs: ["HREF"] };
allowedTagsExtended["TR"] = { attrs: ["ROWSPAN"] };
allowedTagsExtended["TD"] = { attrs: ["COLSPAN", "ROWSPAN"] };
allowedTagsExtended["TH"] = { attrs: ["COLSPAN", "ROWSPAN"] };
allowedTagsExtended["FONT"] = { attrs: ["COLOR"] };

const allowedStyling = {
    color: true,
    "background-color": true,
    "font-weight": true,
    "font-style": true,
    "text-decoration-line": true,
};

let isNightMode = function (): boolean {
    return document.body.classList.contains("nightMode");
};

let filterExternalSpan = function (elem: HTMLElement) {
    // filter out attributes
    for (const attr of [...elem.attributes]) {
        const attrName = attr.name.toUpperCase();

        if (attrName !== "STYLE") {
            elem.removeAttributeNode(attr);
        }
    }

    // filter styling
    for (const name of [...elem.style]) {
        const value = elem.style.getPropertyValue(name);

        if (
            !allowedStyling.hasOwnProperty(name) ||
            // google docs adds this unnecessarily
            (name === "background-color" && value === "transparent") ||
            // ignore coloured text in night mode for now
            (isNightMode() && (name === "background-color" || name === "color"))
        ) {
            elem.style.removeProperty(name);
        }
    }
};

allowedTagsExtended["SPAN"] = filterExternalSpan;

// add basic tags to extended
Object.assign(allowedTagsExtended, allowedTagsBasic);

function isHTMLElement(elem: Element): elem is HTMLElement {
    return elem instanceof HTMLElement;
}

// filtering from another field
let filterInternalNode = function (elem: Element) {
    if (isHTMLElement(elem)) {
        elem.style.removeProperty("background-color");
        elem.style.removeProperty("font-size");
        elem.style.removeProperty("font-family");
    }
    // recurse
    for (let i = 0; i < elem.children.length; i++) {
        const child = elem.children[i];
        filterInternalNode(child);
    }
};

// filtering from external sources
let filterNode = function (node: Node, extendedMode: boolean): void {
    if (node.nodeType === Node.COMMENT_NODE) {
        node.parentNode.removeChild(node);
        return;
    }
    if (!nodeIsElement(node)) {
        return;
    }

    // descend first, and take a copy of the child nodes as the loop will skip
    // elements due to node modifications otherwise
    for (const child of [...node.childNodes]) {
        filterNode(child, extendedMode);
    }

    if (node.tagName === "ANKITOP") {
        return;
    }

    const tag = extendedMode
        ? allowedTagsExtended[node.tagName]
        : allowedTagsBasic[node.tagName];

    if (!tag) {
        if (!node.innerHTML || node.tagName === "TITLE") {
            node.parentNode.removeChild(node);
        } else {
            node.outerHTML = node.innerHTML;
        }
    } else {
        if (typeof tag === "function") {
            // filtering function provided
            tag(node);
        } else {
            // allowed, filter out attributes
            for (const attr of [...node.attributes]) {
                const attrName = attr.name.toUpperCase();
                if (tag.attrs.indexOf(attrName) === -1) {
                    node.removeAttributeNode(attr);
                }
            }
        }
    }
};
