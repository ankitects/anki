// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findWithinNodeVertex } from "./find-within";
import { nodeIsAlong } from "./match-along";
import type { MatchTree } from "./match-tree";
import type { ElementMatcher } from "./match-type";

/**
 * These functions will not ascend on the starting node, but will descend on the neighbor node
 */
function findAdjacentNode(
    node: Node,
    matcher: ElementMatcher,
    getter: (node: Node) => ChildNode | null,
    vertices: MatchTree[],
): number {
    let sibling = getter(node);
    let alongShift = 0;

    while (sibling && nodeIsAlong(sibling)) {
        alongShift++;
        sibling = getter(sibling);
    }

    if (!sibling) {
        return 0;
    }

    const [within, covers] = findWithinNodeVertex(sibling, matcher);

    if (!covers) {
        return 0;
    }

    const shift = alongShift + 1;
    vertices.push(...within);

    return findAdjacentNode(sibling, matcher, getter, vertices) + shift;
}

function previousSibling(node: Node): ChildNode | null {
    return node.previousSibling;
}

/**
 * @param range: Elements will be searched before the start of this range.
 * @returns Nodes before `range` which satisfy `matcher` or of type ALONG.
 */
export function findBefore(
    range: ChildNodeRange,
    matcher: ElementMatcher,
): MatchTree[] {
    const vertices: MatchTree[] = [];
    const shift = findAdjacentNode(
        range.parent.childNodes[range.startIndex],
        matcher,
        previousSibling,
        vertices,
    );
    range.startIndex -= shift;

    return vertices.reverse();
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

/**
 * @param range: Elements will be searched after the end of this range.
 * @returns Nodes after `range` which satisfy `matcher` or of type ALONG.
 */
export function findAfter(range: ChildNodeRange, matcher: ElementMatcher): MatchTree[] {
    const vertices: MatchTree[] = [];
    const shift = findAdjacentNode(
        range.parent.childNodes[range.endIndex - 1],
        matcher,
        nextSibling,
        vertices,
    );

    range.endIndex += shift;

    return vertices;
}
