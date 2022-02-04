// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import type { ChildNodeRange } from "./child-node-range";
import type { ElementMatcher, FoundMatch } from "./matcher";
import { nodeWithinRange } from "./within-range";

/**
 * Elements returned should be in post-order
 */
function findWithinNodeInner(
    node: Node,
    matcher: ElementMatcher,
    matches: FoundMatch[],
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
export function findWithinNode(node: Node, matcher: ElementMatcher): FoundMatch[] {
    const matches: FoundMatch[] = [];

    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches);
        }
    }

    return matches;
}

export function findWithinRange(range: Range, matcher: ElementMatcher): FoundMatch[] {
    const matches: FoundMatch[] = [];

    findWithinNodeInner(range.commonAncestorContainer, matcher, matches);

    return matches.filter((match: FoundMatch): boolean =>
        nodeWithinRange(match.element, range),
    );
}

export function findWithin(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundMatch[] {
    const { parent, startIndex, endIndex } = childNodeRange;
    const matches: FoundMatch[] = [];

    for (const node of Array.prototype.slice.call(
        parent.childNodes,
        startIndex,
        endIndex,
    )) {
        findWithinNodeInner(node, matcher, matches);
    }

    return matches;
}
