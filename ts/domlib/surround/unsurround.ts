// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRangeAnchors } from "./range-anchors";
import { nodeWithinRange } from "./text-node";
import type { SurroundNoSplittingResult } from "./no-splitting";
import { MatchResult, matchTagName } from "./matcher";
import type { ElementMatcher, ElementClearer } from "./matcher";
import { findFarthest } from "./find-above";
import { findWithinNode } from "./find-within";
import type { FoundWithin } from "./find-within";
import { surround } from "./no-splitting";

function findContained(
    commonAncestor: Node,
    range: Range,
    matcher: ElementMatcher,
): FoundWithin[] {
    const matches = findWithinNode(commonAncestor, matcher);
    const contained = matches.filter(({ element }) => nodeWithinRange(range)(element));

    return contained;
}

function unsurroundAdjacent(
    node: Element,
    isKeep: boolean,
    nodesToRemove: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
    condition: (node: Node) => boolean = () => true,
): void {
    const matches = findWithinNode(node, matcher);

    for (const { matchType, element } of matches) {
        if (matchType === MatchResult.MATCH) {
            if (condition(element)) {
                nodesToRemove.push(element);
            }
        } /* matchType === MatchResult.KEEP */ else {
            // order is very important here as `clearer` is idempotent!
            if (condition(element) && clearer(element)) {
                nodesToRemove.push(element);
            }
        }
    }

    if (condition(node) && (!isKeep || clearer(node))) {
        nodesToRemove.push(node);
    }
}

/**
 * Avoids splitting existing elements in the surrounded area
 * might create multiple of the surrounding element and remove elements specified by matcher
 * can be used for inline elements e.g. <b>, or <strong>
 * @param range: The range to surround
 * @param surroundNode: This node will be shallowly cloned for surrounding
 * @param base: Surrounding will not ascent beyond this point; base.contains(range.commonAncestorContainer) should be true
 * @param matcher: Used to detect elements will are similar to the surroundNode, and are included in normalization
 * @param clearer: Used to detect elements will are similar to the surroundNode, and are included in normalization
 **/
export function unsurround(
    range: Range,
    surroundNode: Element,
    base: Element,
    matcher: ElementMatcher = matchTagName(surroundNode.tagName),
    clearer: ElementClearer = () => false,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, matcher);

    const addedNodes: Node[] = [];
    const removedNodes: Node[] = [];
    const nodesToRemove: Element[] = [];

    const aboveStart = findFarthest(range.startContainer, base, matcher);
    const contained = findContained(range.commonAncestorContainer, range, matcher);
    const aboveEnd = findFarthest(range.endContainer, base, matcher);

    const beforeRange = new Range();
    beforeRange.setEnd(range.startContainer, range.startOffset);
    beforeRange.collapse(false);

    if (aboveStart) {
        const [node, isKeep] = aboveStart;
        beforeRange.setStartBefore(node);

        function condition(node: Node): boolean {
            return !node.contains(range.endContainer);
        }

        unsurroundAdjacent(node, isKeep, nodesToRemove, matcher, clearer, condition);
    }

    for (const { element: found } of contained) {
        removedNodes.push(found);
        found.replaceWith(...found.childNodes);
    }

    const afterRange = new Range();
    afterRange.setStart(range.endContainer, range.endOffset);
    afterRange.collapse(true);

    if (aboveEnd) {
        const [node, isKeep] = aboveEnd;
        afterRange.setEndAfter(node);

        unsurroundAdjacent(node, isKeep, nodesToRemove, matcher, clearer);
    }

    if (beforeRange.toString().length > 0) {
        const { addedNodes: added, removedNodes: removed } = surround(
            beforeRange,
            surroundNode,
            base,
            matcher,
            clearer,
        );
        addedNodes.push(...added);
        removedNodes.push(...removed);
    }

    if (afterRange.toString().length > 0) {
        const { addedNodes: added, removedNodes: removed } = surround(
            afterRange,
            surroundNode,
            base,
            matcher,
            clearer,
        );
        addedNodes.push(...added);
        removedNodes.push(...removed);
    }

    for (const node of nodesToRemove) {
        if (node.isConnected) {
            removedNodes.push(node);
            node.replaceWith(...node.childNodes);
        }
    }

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return {
        addedNodes,
        removedNodes,
        surroundedRange,
    };
}
