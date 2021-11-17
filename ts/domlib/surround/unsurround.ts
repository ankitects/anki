// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRangeAnchors } from "./range-anchors";
import { nodeWithinRange } from "./text-node";
import type { SurroundNoSplittingResult } from "./no-splitting";
import { matchTagName } from "./matcher";
import type { ElementMatcher, ElementClearer } from "./matcher";
import { findFarthest } from "./find-above";
import { findWithinNode } from "./find-within";
import { surround } from "./no-splitting";

function findContained(commonAncestor: Node, range: Range, matcher: ElementMatcher) {
    const { matches, keepMatches } = findWithinNode(commonAncestor, matcher);

    return {
        matches: matches.filter(nodeWithinRange(range)),
        keepMatches: keepMatches.filter(nodeWithinRange(range)),
    };
}

function unsurroundAdjacent(
    node: Element,
    isKeep: boolean,
    nodesToRemove: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
    condition: (node: Node) => boolean = () => true,
) {
    const { matches, keepMatches } = findWithinNode(node, matcher);

    for (const match of matches) {
        if (condition(match)) {
            nodesToRemove.push(match);
        }
    }

    for (const match of keepMatches) {
        // order is very important here as `clearer` is idempotent!
        if (condition(match) && clearer(match)) {
            nodesToRemove.push(match);
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

    const aboveStart = findFarthest(range.startContainer, base, matcher);
    const contained = findContained(range.commonAncestorContainer, range, matcher);
    const aboveEnd = findFarthest(range.endContainer, base, matcher);

    const beforeRange = new Range();
    beforeRange.setEnd(range.startContainer, range.startOffset);
    beforeRange.collapse(false);

    const nodesToRemove: Element[] = [];
    if (aboveStart) {
        const [node, isKeep] = aboveStart;
        beforeRange.setStartBefore(node);

        function condition(node: Node): boolean {
            return !node.contains(range.endContainer);
        }

        unsurroundAdjacent(node, isKeep, nodesToRemove, matcher, clearer, condition);
    }

    for (const found of contained.matches) {
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
