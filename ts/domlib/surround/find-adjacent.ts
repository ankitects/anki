// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    elementIsBlock,
    elementIsEmpty,
    nodeIsComment,
    nodeIsElement,
    nodeIsText,
} from "../../lib/dom";
import { hasOnlyChild } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import type { AlongType, ElementMatcher, FoundAdjacent, FoundAlong } from "./match-type";
import { applyMatcher, MatchType } from "./match-type";

function descendToSingleChild(node: Node): ChildNode | null {
    // TODO We refuse descending into block-level elements, which seems like
    // upstream logic. Maybe we should report them as found, but give them an extra
    // flag, like found.descendedIntoBlock
    return nodeIsElement(node) && !elementIsBlock(node) && hasOnlyChild(node)
        ? node.firstChild
        : null;
}

function isAlong(node: Node): node is AlongType {
    return (
        (nodeIsElement(node) && elementIsEmpty(node)) ||
        (nodeIsText(node) && node.length === 0) ||
        nodeIsComment(node)
    );
}

/**
 * These functions will not ascend on the starting node, but will descend on the neighbor node
 */
function findAdjacentNode(
    node: Node,
    matches: FoundAdjacent[],
    matcher: ElementMatcher,
    getter: (node: Node) => ChildNode | null,
): number {
    let current = getter(node);

    const alongs: FoundAlong[] = [];
    while (current && isAlong(current)) {
        alongs.push({
            match: { type: MatchType.ALONG },
            element: current,
        });
        current = getter(current);
    }

    // The element before descending
    const element = current;

    while (current && nodeIsElement(current)) {
        const match = applyMatcher(matcher, current);

        if (match.type) {
            const shift = alongs.length + 1;

            matches.push(
                ...alongs, {
                element: current as HTMLElement | SVGElement,
                match,
            });

            return findAdjacentNode(element!, matches, matcher, getter) + shift;
        }

        current = descendToSingleChild(current);
    }

    return 0;
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
): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    const shift = findAdjacentNode(
        range.parent.childNodes[range.startIndex],
        matches,
        matcher,
        previousSibling
    );
    range.startIndex -= shift;

    return matches;
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

/**
 * @param range: Elements will be searched after the end of this range.
 * @returns Nodes after `range` which satisfy `matcher` or of type ALONG.
 */
export function findAfter(
    range: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    const shift = findAdjacentNode(
    range.parent.childNodes[range.endIndex - 1],
        matches,
        matcher,
        nextSibling,
    );

    range.endIndex += shift;

    return matches;
}
