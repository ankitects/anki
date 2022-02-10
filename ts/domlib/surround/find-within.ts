// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock,nodeIsElement } from "../../lib/dom";
import type { ChildNodeRange } from "./child-node-range";
import { nodeIsAlong } from "./match-along";
import { MatchTree } from "./match-tree";
import type { ElementMatcher, FoundMatch } from "./match-type";
import { applyMatcher } from "./match-type";
import { nodeWithinRange } from "./within-range";

export function findWithinNodeVertex(
    node: Node,
    matcher: ElementMatcher,
): [MatchTree[], boolean] {
    if (nodeIsAlong(node)) {
        return [[], true];
    }

    if (!nodeIsElement(node)) {
        return [[], false];
    }

    const nested: MatchTree[] = [];
    let covers = !elementIsBlock(node);

    for (const child of node.children) {
        const [vertices, coverInner] = findWithinNodeVertex(child, matcher);

        nested.push(...vertices);
        covers = covers && coverInner;
    }

    const match = applyMatcher(matcher, node);

    if (match.type) {
        return [
            [MatchTree.make(nested, { match, element: node as HTMLElement | SVGElement })],
            true,
        ];
    }

    return [nested, covers];
}

/**
 * Elements pushed to matches are be in postorder
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

        const match = applyMatcher(matcher, node);
        if (match.type) {
            matches.push({ match, element: node as HTMLElement | SVGElement });
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
    range: ChildNodeRange,
    matcher: ElementMatcher,
): FoundMatch[] {
    const { parent, startIndex, endIndex } = range;
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
