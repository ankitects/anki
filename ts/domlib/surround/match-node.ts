// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    elementIsEmpty,
    nodeIsComment,
    nodeIsElement,
    nodeIsText,
} from "../../lib/dom";

/**
 * A "negligible" node is a node that can be surrounded without a semantic change
 */
export function nodeIsNegligible(node: Node): boolean {
    return (
        (nodeIsElement(node) && elementIsEmpty(node)) ||
        (nodeIsText(node) && node.length === 0) ||
        nodeIsComment(node)
    );
}

export function nodeIsAmongNegligibles(node: Node): boolean {
    let previous = node.previousSibling;
    while (previous) {
        if (!nodeIsNegligible(previous)) {
            return false;
        }
        previous = previous.previousSibling;
    }

    let next = node.nextSibling;
    while (next) {
        if (!nodeIsNegligible(next)) {
            return false;
        }
        next = next.nextSibling;
    }

    return true;
}
