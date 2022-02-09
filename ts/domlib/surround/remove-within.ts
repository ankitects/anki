// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findWithin } from "./find-within";
import type { ElementMatcher, FoundMatch } from "./matcher";
import { MatchType } from "./matcher";

function countChildNodesRespectiveToParent(parent: Node, element: Element): number {
    return element.parentNode === parent ? element.childNodes.length : 1;
}

/**
 * @param removed: Removed elements will be pushed onto this array
 */
function normalizeWithin(
    matches: FoundMatch[],
    parent: Node,
    removed: Element[],
): number {
    let childCount = 0;

    for (const { match, element } of matches) {
        if (!match.type) {
            continue;
        }

        if (match.type === MatchType.CLEAR && !match.clear(element)) {
            childCount += 1;
        }

        removed.push(element);
        childCount += countChildNodesRespectiveToParent(parent, element);
        element.replaceWith(...element.childNodes);
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
): Element[] {
    const removed: Element[] = [];
    const matches = findWithin(range, matcher);
    const shift = normalizeWithin(matches, range.parent, removed);
    range.endIndex += shift;

    return removed;
}
