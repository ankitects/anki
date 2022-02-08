// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import { ascend } from "../../lib/node";

export interface ChildNodeRange {
    parent: Node;
    startIndex: number;
    /* exclusive end */
    endIndex: number;
}

/**
 * @remarks
 * Indices should be >= 0 and startIndex < endIndex
 */
function makeChildNodeRange(
    node: Node,
    startIndex: number,
    endIndex = startIndex + 1,
): ChildNodeRange {
    return {
        parent: node,
        startIndex,
        endIndex,
    };
}

/**
 * @remarks
 * The new child node range might not necessarily indicate the node itself but
 * could also be a supposed new node that entirely surrounds the passed in node
 */
export function nodeToChildNodeRange(node: Node): ChildNodeRange {
    const parent = ascend(node);
    const index = Array.prototype.indexOf.call(parent.childNodes, node);

    return makeChildNodeRange(parent, index);
}

function toDOMRange(childNodeRange: ChildNodeRange): Range {
    const range = new Range();
    range.setStart(childNodeRange.parent, childNodeRange.startIndex);
    range.setEnd(childNodeRange.parent, childNodeRange.endIndex);

    return range;
}

export function surroundChildNodeRangeWithNode(
    childNodeRange: ChildNodeRange,
    node: Node,
): void {
    const range = toDOMRange(childNodeRange);

    if (range.collapsed) {
        /**
         * If the range is collapsed to a single element, move the range inside the element.
         * This prevents putting the surround above the base element.
         */
        const selected = range.commonAncestorContainer.childNodes[range.startOffset];

        if (nodeIsElement(selected)) {
            range.selectNode(selected);
        }
    }

    range.surroundContents(node);
}
