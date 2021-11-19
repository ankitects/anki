// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, elementIsEmpty } from "../../lib/dom";
import { hasOnlyChild } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import { MatchResult } from "./matcher";
import type { ElementMatcher } from "./matcher";

/**
 * These functions will not ascend on the starting node, but will descend on the neighbor node
 */
function adjacentNodeInner(getter: (node: Node) => ChildNode | null) {
    function findAdjacentNodeInner(
        node: Node,
        matches: Element[],
        keepMatches: Element[],
        along: Element[],
        matcher: ElementMatcher,
    ): void {
        const adjacent = getter(node);

        if (adjacent && nodeIsElement(adjacent)) {
            let current: Element | null = adjacent;

            const maybeAlong: Element[] = [];
            while (nodeIsElement(current) && elementIsEmpty(current)) {
                const adjacentNext = getter(current);
                maybeAlong.push(current);

                if (!adjacentNext || !nodeIsElement(adjacentNext)) {
                    return;
                } else {
                    current = adjacentNext;
                }
            }

            while (current) {
                const matchResult = matcher(current);

                if (matchResult) {
                    along.push(...maybeAlong);

                    switch (matchResult) {
                        case MatchResult.MATCH:
                            matches.push(current);
                            break;
                        case MatchResult.KEEP:
                            keepMatches.push(current);
                            break;
                    }

                    return findAdjacentNodeInner(
                        current,
                        matches,
                        keepMatches,
                        along,
                        matcher,
                    );
                }

                // descend down into element
                current =
                    hasOnlyChild(current) && nodeIsElement(current.firstChild!)
                        ? current.firstChild
                        : null;
            }
        }
    }

    return findAdjacentNodeInner;
}

interface FindAdjacentResult {
    /* elements adjacent which match matcher */
    matches: Element[];
    keepMatches: Element[];
    /* element adjacent between found elements, which can
     * be safely skipped (e.g. empty elements) */
    along: Element[];
}

const findBeforeNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.previousSibling,
);

function findBeforeNode(node: Node, matcher: ElementMatcher): FindAdjacentResult {
    const matches: Element[] = [];
    const keepMatches: Element[] = [];
    const along: Element[] = [];

    findBeforeNodeInner(node, matches, keepMatches, along, matcher);

    return { matches, keepMatches, along };
}

export function findBefore(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FindAdjacentResult {
    const { parent, startIndex } = childNodeRange;
    return findBeforeNode(parent.childNodes[startIndex], matcher);
}

const findAfterNodeInner = adjacentNodeInner(
    (node: Node): ChildNode | null => node.nextSibling,
);

function findAfterNode(node: Node, matcher: ElementMatcher): FindAdjacentResult {
    const matches: Element[] = [];
    const keepMatches: Element[] = [];
    const along: Element[] = [];

    findAfterNodeInner(node, matches, keepMatches, along, matcher);

    return { matches, keepMatches, along };
}

export function findAfter(
    childNodeRange: ChildNodeRange,
    matcher: ElementMatcher,
): FindAdjacentResult {
    const { parent, endIndex } = childNodeRange;
    return findAfterNode(parent.childNodes[endIndex - 1], matcher);
}
