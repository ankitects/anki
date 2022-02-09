// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    elementIsEmpty,
    nodeIsElement,
    nodeIsText,
    nodeIsComment,
} from "../../lib/dom";
import { hasOnlyChild } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import type { ElementMatcher, FoundAdjacent, FoundAlong, AlongType } from "./matcher";
import { applyMatcher, MatchType } from "./matcher";

function descendToSingleChild(node: ChildNode): ChildNode | null {
    return hasOnlyChild(node) && nodeIsElement(node.firstChild!)
        ? node.firstChild
        : null;
}

function isAlong(node: ChildNode): node is AlongType {
    return (
        (nodeIsElement(node) && elementIsEmpty(node)) ||
        (nodeIsText(node) && node.length === 0) ||
        nodeIsComment(node)
    );
}

/**
 * These functions will not ascend on the starting node, but will descend on the neighbor node
 */
function adjacentNodeInner(getter: (node: Node) => ChildNode | null) {
    return function inner(
        node: Node,
        matches: FoundAdjacent[],
        matcher: ElementMatcher,
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

                return inner(element!, matches, matcher);
            }

            current = descendToSingleChild(current);
        }
    };
}

const findBeforeNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.previousSibling,
);

const findAfterNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.nextSibling,
);

function findBeforeNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findBeforeNodeInner(node, matches, matcher);
    return matches;
}

function findAfterNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findAfterNodeInner(node, matches, matcher);
    return matches;
}

export function findBefore(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, startIndex } = childNodeRange;
    return findBeforeNode(parent.childNodes[startIndex], matcher);
}

export function findAfter(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, endIndex } = childNodeRange;
    return findAfterNode(parent.childNodes[endIndex - 1], matcher);
}
