// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsEmpty, nodeIsElement, nodeIsText } from "../../lib/dom";
import { hasOnlyChild } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import type { ElementMatcher, FoundAdjacent, FoundAlong } from "./matcher";
import { MatchResult } from "./matcher";

/**
 * These functions will not ascend on the starting node, but will descend on the neighbor node
 */
function adjacentNodeInner(getter: (node: Node) => ChildNode | null) {
    function findAdjacentNodeInner(
        node: Node,
        matches: FoundAdjacent[],
        matcher: ElementMatcher,
    ): void {
        let current = getter(node);

        const maybeAlong: (Element | Text)[] = [];
        while (
            current &&
            ((nodeIsElement(current) && elementIsEmpty(current)) ||
                (nodeIsText(current) && current.length === 0))
        ) {
            maybeAlong.push(current);
            current = getter(current);
        }

        while (current && nodeIsElement(current)) {
            const element: Element = current;
            const matchResult = matcher(element);

            if (matchResult) {
                matches.push(
                    ...maybeAlong.map(
                        (along: Element | Text): FoundAlong => ({
                            element: along,
                            matchType: MatchResult.ALONG,
                        }),
                    ),
                );

                matches.push({
                    element,
                    matchType: matchResult,
                });

                return findAdjacentNodeInner(element, matches, matcher);
            }

            // descend down into element
            current =
                hasOnlyChild(current) && nodeIsElement(element.firstChild!)
                    ? element.firstChild
                    : null;
        }
    }

    return findAdjacentNodeInner;
}

const findBeforeNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.previousSibling,
);

function findBeforeNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findBeforeNodeInner(node, matches, matcher);
    return matches;
}

export function findBefore(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, startIndex } = childNodeRange;
    return findBeforeNode(parent.childNodes[startIndex], matcher);
}

const findAfterNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.nextSibling,
);

function findAfterNode(node: Node, matcher: ElementMatcher): FoundAdjacent[] {
    const matches: FoundAdjacent[] = [];
    findAfterNodeInner(node, matches, matcher);
    return matches;
}

export function findAfter(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FoundAdjacent[] {
    const { parent, endIndex } = childNodeRange;
    return findAfterNode(parent.childNodes[endIndex - 1], matcher);
}
