// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText, elementIsEmpty } from "../../lib/dom";
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
        along: (Element | Text)[],
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
                along.push(...maybeAlong);

                switch (matchResult) {
                    case MatchResult.MATCH:
                        matches.push(element);
                        break;
                    case MatchResult.KEEP:
                        keepMatches.push(element);
                        break;
                }

                return findAdjacentNodeInner(
                    element,
                    matches,
                    keepMatches,
                    along,
                    matcher,
                );
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
