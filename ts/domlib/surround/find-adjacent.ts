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
import type { AlongType, ElementMatcher, FoundAdjacent, FoundAlong } from "./matcher";
import { applyMatcher, MatchType } from "./matcher";

function descendToSingleChild(node: Node): ChildNode | null {
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
): void {
    let current = getter(node);

    const alongs: AlongType[] = [];
    while (current && isAlong(current)) {
        alongs.push(current);
        current = getter(current);
    }

    // The element before descending
    const element = current;

    while (current && nodeIsElement(current)) {
        const match = applyMatcher(matcher, current);

        if (match.type) {
            matches.push(
                ...alongs.map(
                    (along: AlongType): FoundAlong => ({
                        element: along,
                        match: { type: MatchType.ALONG },
                    }),
                ),
            );

            matches.push({
                element: current as HTMLElement | SVGElement,
                match,
            });

            return findAdjacentNode(element!, matches, matcher, getter);
        }

        current = descendToSingleChild(current);
    }
}

function findBeforeNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findAdjacentNode(
        node,
        matches,
        matcher,
        (node: Node): ChildNode | null => node.previousSibling,
    );
    return matches;
}

function findAfterNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findAdjacentNode(
        node,
        matches,
        matcher,
        (node: Node): ChildNode | null => node.nextSibling,
    );
    return matches;
}

export function findBefore(
    range: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, startIndex } = range;
    return findBeforeNode(parent.childNodes[startIndex], matcher);
}

export function findAfter(
    range: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, endIndex } = range;
    return findAfterNode(parent.childNodes[endIndex - 1], matcher);
}
