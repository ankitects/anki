// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import {
    nodeToChildNodeRange,
    areSiblingChildNodeRanges,
    mergeChildNodeRanges,
    coversWholeParent,
} from "./child-node-range";
import { ascendWhileSingleInline } from "./ascend";

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
 * @example When surround with <b>
 * <b><u>Hello </u></b><b><i>World</i></b> will be merged to
 * <b><u>Hello </u><i>World</i></b>
 */
const tryMergingTillMismatch =
    (base: Element) =>
    (
        { mismatch, minimized /* must be nonempty */ }: MergeMatch,
        childNodeRange: ChildNodeRange,
    ): MergeMatch => {
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
                              ascendWhileSingleInline(
                                  mergedChildNodeRange.parent,
                                  base,
                              ),
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
    };

function getMergeMatcher(base: Element) {
    function mergeMatchInner(
        accu: ChildNodeRange[],
        childNodeRange: ChildNodeRange,
    ): ChildNodeRange[] {
        return [...accu].reduceRight(
            tryMergingTillMismatch(base),
            createInitialMergeMatch(childNodeRange),
        ).minimized;
    }

    return mergeMatchInner;
}

export function mergeMatchChildNodeRanges(
    ranges: ChildNodeRange[],
    base: Element,
): ChildNodeRange[] {
    return ranges.reduce(getMergeMatcher(base), []);
}
