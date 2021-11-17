// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import type { ChildNodeRange } from "./child-node-range";
import type { MatchResult, ElementMatcher } from "./matcher";

export interface FoundWithin {
    matchType: Exclude<MatchResult, MatchResult.NO_MATCH>;
    element: Element;
}

/**
 * Elements returned should be in post-order
 */
function findWithinNodeInner(
    node: Node,
    matcher: ElementMatcher,
    matches: FoundWithin[],
): void {
    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }

        const matchType = matcher(node);
        if (matchType) {
            matches.push({ matchType, element: node });
        }
    }
}

/**
 * Will not include parent node
 */
export function findWithinNode(node: Node, matcher: ElementMatcher): FoundWithin[] {
    const matches: FoundWithin[] = [];

    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }
    }

    return matches;
}

export function findWithin(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundWithin[] {
    const { parent, startIndex, endIndex } = childNodeRange;
    const matches: FoundWithin[] = [];

    for (let node of Array.prototype.slice.call(
        parent.childNodes,
        startIndex,
        endIndex,
    )) {
        findWithinNodeInner(node, matcher, matches);
    }

    return matches;
}
