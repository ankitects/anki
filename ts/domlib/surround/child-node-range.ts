// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ascend } from "../../lib/node";
import { nodeIsElement, elementIsEmpty } from "../../lib/dom";

export interface ChildNodeRange {
    parent: Node;
    startIndex: number;
    /* exclusive end */
    endIndex: number;
}

/**
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
 * Result does not indicate the node itself but a supposed new node that
 * entirely surrounds the passed in node
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

export function areSiblingChildNodeRanges(
    before: ChildNodeRange,
    after: ChildNodeRange,
): boolean {
    if (before.parent !== after.parent || before.endIndex > after.startIndex) {
        return false;
    }

    if (before.endIndex === after.startIndex) {
        return true;
    }

    for (let index = before.endIndex; index < after.startIndex; index++) {
        const node = before.parent.childNodes[index];

        if (!nodeIsElement(node) || !elementIsEmpty(node)) {
            return false;
        }
    }

    return true;
}

export function coversWholeParent(childNodeRange: ChildNodeRange): boolean {
    return (
        childNodeRange.startIndex === 0 &&
        childNodeRange.endIndex === childNodeRange.parent.childNodes.length
    );
}

/**
 * Precondition: must be sibling child node ranges
 */
export function mergeChildNodeRanges(
    before: ChildNodeRange,
    after: ChildNodeRange,
): ChildNodeRange {
    return {
        parent: before.parent,
        startIndex: before.startIndex,
        endIndex: after.endIndex,
    };
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
