// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findWithin } from "./find-within";
import { countChildNodesRespectiveToParent } from "./helpers";
import type { ElementClearer, ElementMatcher, FoundMatch } from "./matcher";
import { MatchResult } from "./matcher";

function normalizeWithin(
    matches: FoundMatch[],
    parent: Node,
    removedNodes: Element[],
    clearer: ElementClearer,
): number {
    let childCount = 0;

    for (const { matchType, element } of matches) {
        if (matchType === MatchResult.MATCH) {
            removedNodes.push(element);
            childCount += countChildNodesRespectiveToParent(parent, element);
            element.replaceWith(...element.childNodes);
        } /* matchType === MatchResult.KEEP */ else {
            if (clearer(element)) {
                removedNodes.push(element);
                childCount += countChildNodesRespectiveToParent(parent, element);
                element.replaceWith(...element.childNodes);
            } else {
                childCount += 1;
            }
        }
    }

    const shift = childCount - matches.length;
    return shift;
}

/**
 * Removes matching elements while adjusting child node ranges accordingly
 *
 * @remarks
 * Amount of ranges is guaranteed not to change during this normalization step.
 * Child node ranges might exceed original range provided to surround after this
 * function, if the to be surrounded range is preceded or suceeded by a matching
 * element.
 *
 * @example
 * Surrounding with bold, a child node range of a span from 0 to 3 on:
 * `<span>single<b>double</b>single</b></span>` becomes a range of the same span
 * from 0 to 1 on this: `<span>singledoublesingle</span>`.
 *
 * @example
 * Surrounding with bold, a child node range of a span from 0 to 2 on:
 * `<span><i><b>before</b></i><b>after</b></span>` becomes a range of the same
 * span from 0 to 2 on `<span><i>before</i>after</span>`.
 */
export function removeWithin(
    ranges: ChildNodeRange[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
): Element[] {
    const removedNodes: Element[] = [];

    for (const range of ranges) {
        const matches = findWithin(range, matcher);
        const withinShift = normalizeWithin(
            matches,
            range.parent,
            removedNodes,
            clearer,
        );
        range.endIndex += withinShift;
    }

    return removedNodes;
}
