// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { nodeToChildNodeRange } from "./child-node-range";
import { ascendWhileSingleInline } from "./helpers";
import { nodeIsElement, elementIsEmpty } from "../../lib/dom";

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

interface MergeMatch {
    mismatch: boolean;
    minimized: ChildNodeRange[];
}

function createInitialMergeMatch(childNodeRange: ChildNodeRange): MergeMatch {
    return {
        mismatch: false,
        minimized: [childNodeRange],
    };
}

/**
 * After an _inner match_, we right-reduce the existing matches
 * to see if any existing inner matches can be matched to one bigger match
 *
 * @example
 * When surround with <b>:
 * <b><u>Hello</u></b><b><i>World</i></b> will be merged to
 * <b><u>Hello</u><i>World</i></b>
 */
function tryMergingTillMismatch(
    { mismatch, minimized /* must be nonempty */ }: MergeMatch,
    childNodeRange: ChildNodeRange,
    base: Element,
): MergeMatch {
    if (mismatch) {
        return {
            mismatch: true,
            minimized: [childNodeRange, ...minimized],
        };
    }

    const [nextChildNodeRange, ...restChildNodeRanges] = minimized;

    if (areSiblingChildNodeRanges(childNodeRange, nextChildNodeRange)) {
        const mergedChildNodeRange = mergeChildNodeRanges(
            childNodeRange,
            nextChildNodeRange,
        );

        const newChildNodeRange =
            coversWholeParent(mergedChildNodeRange) &&
            mergedChildNodeRange.parent !== base
                ? nodeToChildNodeRange(
                      ascendWhileSingleInline(mergedChildNodeRange.parent, base),
                  )
                : mergedChildNodeRange;

        return {
            mismatch: false,
            minimized: [newChildNodeRange, ...restChildNodeRanges],
        };
    } else {
        return {
            mismatch: true,
            minimized: [childNodeRange, ...minimized],
        };
    }
}

function getMergeMatcher(
    ranges: ChildNodeRange[],
    childNodeRange: ChildNodeRange,
    base: Element,
): ChildNodeRange[] {
    let mergeMatch = createInitialMergeMatch(childNodeRange);

    for (let i = ranges.length - 1; i >= 0; i--) {
        mergeMatch = tryMergingTillMismatch(mergeMatch, ranges[i], base);
    }

    return mergeMatch.minimized;
}

export function mergeRanges(ranges: ChildNodeRange[], base: Element): ChildNodeRange[] {
    let result: ChildNodeRange[] = [];

    for (const range of ranges) {
        result = getMergeMatcher(result, range, base);
    }

    return result;
}
