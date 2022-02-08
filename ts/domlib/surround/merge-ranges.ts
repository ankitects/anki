// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import {
    areSiblingChildNodeRanges,
    coversWholeParent,
    mergeChildNodeRanges,
    nodeToChildNodeRange,
} from "./child-node-range";
import { ascendWhileSingleInline } from "./helpers";

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
            mismatch,
            minimized: [childNodeRange, ...minimized],
        };
    } else {
        const [nextChildNodeRange, ...restChildNodeRanges] = minimized;

        if (
            areSiblingChildNodeRanges(
                childNodeRange,
                nextChildNodeRange,
            ) /* && !childNodeRange.parent === base */
        ) {
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
                mismatch,
                minimized: [newChildNodeRange, ...restChildNodeRanges],
            };
        } else {
            return {
                mismatch: true,
                minimized: [childNodeRange, ...minimized],
            };
        }
    }
}

function getMergeMatcher(
    accu: ChildNodeRange[],
    childNodeRange: ChildNodeRange,
    base: Element,
): ChildNodeRange[] {
    let accuInner = createInitialMergeMatch(childNodeRange);

    for (let i = accu.length - 1; i >= 0; i--) {
        accuInner = tryMergingTillMismatch(accuInner, accu[i], base);
    }

    return accuInner.minimized;
}

export function mergeRanges(ranges: ChildNodeRange[], base: Element): ChildNodeRange[] {
    let result: ChildNodeRange[] = [];
    for (const range of ranges) {
        result = getMergeMatcher(result, range, base);
    }

    return result;
}
