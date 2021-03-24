/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import { nodeIsElement } from "./helpers";
import { tagsAllowedBasic, tagsAllowedExtended } from "./htmlFilterTagsAllowed";

////////////////////// //////////////////// ////////////////////

function isHTMLElement(elem: Element): elem is HTMLElement {
    return elem instanceof HTMLElement;
}

// filtering from another field
function filterInternalNode(elem: Element): void {
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
}

// filtering from external sources
function filterNode(node: Node, extendedMode: boolean): void {
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

    const tagsAllowed = extendedMode ? tagsAllowedExtended : tagsAllowedBasic;

    if (tagsAllowed.hasOwnProperty(node.tagName)) {
        tagsAllowed[node.tagName](node);
    } else {
        if (!node.innerHTML || node.tagName === "TITLE") {
            node.parentNode.removeChild(node);
        } else {
            node.outerHTML = node.innerHTML;
        }
    }
}

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
