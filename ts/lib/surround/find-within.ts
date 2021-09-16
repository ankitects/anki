// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../dom";
import type { ChildNodeRange } from "./child-node-range";
import { MatchResult } from "./matcher";
import type { ElementMatcher } from "./matcher";

/**
 * Elements returned should be in post-order
 */

function findWithinNodeInner(
    node: Node,
    matcher: ElementMatcher,
    matches: Element[],
    keepMatches: Element[],
): void {
    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches, keepMatches);
        }

        const matchResult = matcher(node);

        if (matchResult) {
            switch (matchResult) {
                case MatchResult.MATCH:
                    matches.push(node);
                    break;
                case MatchResult.KEEP:
                    keepMatches.push(node);
                    break;
            }
        }
    }
}

interface FindWithinResult {
    matches: Element[];
    keepMatches: Element[];
}

/**
 * Will not include parent node
 */
export function findWithinNode(node: Node, matcher: ElementMatcher): FindWithinResult {
    const matches: Element[] = [];
    const keepMatches: Element[] = [];

    if (nodeIsElement(node)) {
        for (const child of node.children) {
            findWithinNodeInner(child, matcher, matches, keepMatches);
        }
    }

    return { matches, keepMatches };
}

export function findWithin(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FindWithinResult {
    const { parent, startIndex, endIndex } = childNodeRange;
    const matches: Element[] = [];
    const keepMatches: Element[] = [];

    for (let node of Array.prototype.slice.call(
        parent.childNodes,
        startIndex,
        endIndex,
    )) {
        findWithinNodeInner(node, matcher, matches, keepMatches);
    }

    return { matches, keepMatches };
}
