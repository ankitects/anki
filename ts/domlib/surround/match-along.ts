// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    elementIsEmpty,
    nodeIsComment,
    nodeIsElement,
    nodeIsText,
} from "../../lib/dom";

/**
 * An "along" node is a node that can be surrounded without a semantic change
 */
export function nodeIsAlong(node: Node): boolean {
    return (
        (nodeIsElement(node) && elementIsEmpty(node)) ||
        (nodeIsText(node) && node.length === 0) ||
        nodeIsComment(node)
    );
}
