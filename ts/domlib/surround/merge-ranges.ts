// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsEmpty, nodeIsElement } from "../../lib/dom";
import type { ChildNodeRange } from "./child-node-range";
import { nodeToChildNodeRange } from "./child-node-range";
import { ascendWhileSingleInline } from "./helpers";

export function coversWholeParent(childNodeRange: ChildNodeRange): boolean {
    return (
        childNodeRange.startIndex === 0 &&
        childNodeRange.endIndex === childNodeRange.parent.childNodes.length
    );
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

/**
 * @remarks
 * Must be sibling child node ranges
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

/**
 * @example
 * When surround with <b>:
 * <b><u>Hello</u></b><b><i>World</i></b> will be merged to
 * <b><u>Hello</u><i>World</i></b>
 */
function mergeIfSiblings(
    before: ChildNodeRange,
    after: ChildNodeRange,
    base: Element,
): ChildNodeRange | null {
    if (!areSiblingChildNodeRanges(before, after)) {
        return null;
    }

    const mergedChildNodeRange = mergeChildNodeRanges(before, after);
    const newRange =
        coversWholeParent(mergedChildNodeRange) && mergedChildNodeRange.parent !== base
            ? nodeToChildNodeRange(
                  ascendWhileSingleInline(mergedChildNodeRange.parent, base),
              )
            : mergedChildNodeRange;

    return newRange;
}

function mergeTillFirstMismatch(
    ranges: ChildNodeRange[],
    range: ChildNodeRange,
    base: Element,
): ChildNodeRange[] {
    const minimized = [range];

    for (let i = ranges.length - 1; i >= 0; i--) {
        const newRange = mergeIfSiblings(ranges[i], minimized[0], base);

        if (newRange) {
            minimized[0] = newRange;
        } else {
            minimized.unshift(...ranges.slice(0, i + 1).reverse());
            break;
        }
    }

    return minimized;
}

export function mergeRanges(ranges: ChildNodeRange[], base: Element): ChildNodeRange[] {
    let result: ChildNodeRange[] = [];

    for (const range of ranges) {
        result = mergeTillFirstMismatch(result, range, base);
    }

    return result;
}
