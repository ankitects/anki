// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findWithin } from "./find-within";
import { countChildNodesRespectiveToParent } from "./helpers";
import type { ElementClearer, ElementMatcher, FoundMatch } from "./matcher";
import { MatchResult } from "./matcher";

/**
 * @param removed: Removed elements will be pushed onto this array
 */
function normalizeWithin(
    matches: FoundMatch[],
    parent: Node,
    clearer: ElementClearer,
    removed: Element[],
): number {
    let childCount = 0;

    for (const { matchType, element } of matches) {
        if (matchType === MatchResult.MATCH) {
            removed.push(element);
            childCount += countChildNodesRespectiveToParent(parent, element);
            element.replaceWith(...element.childNodes);
        } /* matchType === MatchResult.KEEP */ else {
            if (clearer(element)) {
                removed.push(element);
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
    range: ChildNodeRange,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): Element[] {
    const removed: Element[] = [];
    const matches = findWithin(range, matcher);
    const shift = normalizeWithin(matches, range.parent, clearer, removed);
    range.endIndex += shift;

    return removed;
}
