// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import { findWithinNode } from "./find-within";
import { countChildNodesRespectiveToParent } from "./helpers";
import type {
    ElementClearer,
    ElementMatcher,
    FoundAdjacent,
    FoundMatch,
} from "./matcher";
import { MatchResult } from "./matcher";

function removeWithinAndCountChildren(
    node: Element,
    parent: Node,
    removedNodes: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
): number {
    const matches = findWithinNode(node, matcher);
    const processFoundMatches = ({ element, matchType }: FoundMatch) =>
        matchType === MatchResult.MATCH ?? clearer(element);

    for (const { element: found } of matches.filter(processFoundMatches)) {
        removedNodes.push(found);
        found.replaceWith(...found.childNodes);
    }

    // Normalization here is vital so that the
    // original range can be selected afterwards
    node.normalize();
    return countChildNodesRespectiveToParent(parent, node);
}

function removeMatchesAndCountChildren(
    matches: FoundAdjacent[],
    parent: Node,
    removedNodes: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
): number {
    let childCount = 0;
    let keepChildCount = 0;

    for (const { element, matchType } of matches) {
        switch (matchType) {
            case MatchResult.MATCH:
                childCount += removeWithinAndCountChildren(
                    element as Element,
                    parent,
                    removedNodes,
                    matcher,
                    clearer,
                );

                removedNodes.push(element as HTMLElement | SVGElement);
                element.replaceWith(...element.childNodes);
                break;

            case MatchResult.KEEP:
                keepChildCount = removeWithinAndCountChildren(
                    element as Element,
                    parent,
                    removedNodes,
                    matcher,
                    clearer,
                );

                if (clearer(element as HTMLElement | SVGElement)) {
                    removedNodes.push(element as HTMLElement | SVGElement);
                    element.replaceWith(...element.childNodes);
                    childCount += keepChildCount;
                } else {
                    childCount++;
                }
                break;

            case MatchResult.ALONG:
                childCount++;
                break;
        }
    }

    return childCount;
}

/**
 * Removes matches positioned before while adjusting child node range
 * The child node range will be adjusted to include those removed nodes.
 *
 * @remarks
 * Modifies range.
 *
 * @returns removedNodes
 */
export function removeBefore(
    range: ChildNodeRange,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): Element[] {
    const removedNodes: Element[] = [];
    const matches = findBefore(range, matcher);
    const count = removeMatchesAndCountChildren(
        matches,
        range.parent,
        removedNodes,
        matcher,
        clearer,
    );

    range.startIndex -= matches.length;
    range.endIndex += count - matches.length;

    return removedNodes;
}

/**
 * Removes matches positioned after range while adjusting child node range.
 * The child node range will be adjusted to include those removed nodes.
 *
 * @remarks
 * Modifies range.
 *
 * @returns removedNodes
 */
export function removeAfter(
    range: ChildNodeRange,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): Element[] {
    const removedNodes: Element[] = [];
    const matches = findAfter(range, matcher);
    const count = removeMatchesAndCountChildren(
        matches,
        range.parent,
        removedNodes,
        matcher,
        clearer,
    );

    range.endIndex += count;

    return removedNodes;
}
