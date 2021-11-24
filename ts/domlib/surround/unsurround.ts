// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRangeAnchors } from "./range-anchors";
import type { NodesResult, SurroundNoSplittingResult } from "./no-splitting";
import { MatchResult, matchTagName } from "./matcher";
import type { FoundMatch, ElementMatcher, ElementClearer } from "./matcher";
import { findFarthest } from "./find-above";
import { findWithinRange, findWithinNode } from "./find-within";
import { surround } from "./no-splitting";

function findBetween(
    range: Range,
    matcher: ElementMatcher,
    aboveStart?: Element | undefined,
    aboveEnd?: Element | undefined,
): FoundMatch[] {
    const betweenRange = range.cloneRange();

    if (aboveStart) {
        betweenRange.setStartAfter(aboveStart);
    }

    if (aboveEnd) {
        betweenRange.setEndBefore(aboveEnd);
    }

    return findWithinRange(betweenRange, matcher);
}

function findAndClearWithin(
    match: FoundMatch,
    matcher: ElementMatcher,
    clearer: ElementClearer,
    condition: (node: Node) => boolean = () => true,
): Element[] {
    const toRemove: Element[] = [];

    for (const { matchType, element } of findWithinNode(match.element, matcher)) {
        if (matchType === MatchResult.MATCH) {
            if (condition(element)) {
                toRemove.push(element);
            }
        } /* matchType === MatchResult.KEEP */ else {
            // order is very important here as `clearer` is idempotent!
            if (condition(element) && clearer(element)) {
                toRemove.push(element);
            }
        }
    }

    if (condition(match.element)) {
        switch (match.matchType) {
            case MatchResult.MATCH:
                toRemove.push(match.element);
                break;
            case MatchResult.KEEP:
                if (clearer(match.element)) {
                    toRemove.push(match.element);
                }
                break;
        }
    }

    return toRemove;
}

function prohibitOverlapse(withNode: Node): (node: Node) => boolean {
    /* otherwise, they will be added to nodesToRemove twice
     * and will also be cleared twice */
    return (node: Node) => !node.contains(withNode) && !withNode.contains(node);
}

interface FindNodesToRemoveResult {
    nodesToRemove: Element[];
    beforeRange: Range;
    afterRange: Range;
}

/**
 * @returns beforeRange: will start at the farthest any of the nodes to remove will
 *  extend in start direction till the start of the original range
 * @return afterRange: will start at the end of the original range and will extend as
 *  far as any of the nodes to remove will extend in end direction
 */
function findNodesToRemove(
    range: Range,
    base: Element,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): FindNodesToRemoveResult {
    const nodesToRemove: Element[] = [];

    const aboveStart = findFarthest(range.startContainer, base, matcher);
    const aboveEnd = findFarthest(range.endContainer, base, matcher);
    const between = findBetween(range, matcher, aboveStart?.element, aboveEnd?.element);

    const beforeRange = new Range();
    beforeRange.setEnd(range.startContainer, range.startOffset);
    beforeRange.collapse(false);

    if (aboveStart) {
        beforeRange.setStartBefore(aboveStart.element);

        const matches = findAndClearWithin(
            aboveStart,
            matcher,
            clearer,
            aboveEnd ? prohibitOverlapse(aboveEnd.element) : () => true,
        );
        nodesToRemove.push(...matches);
    }

    nodesToRemove.push(...between.map((match) => match.element));

    const afterRange = new Range();
    afterRange.setStart(range.endContainer, range.endOffset);
    afterRange.collapse(true);

    if (aboveEnd) {
        afterRange.setEndAfter(aboveEnd.element);

        const matches = findAndClearWithin(aboveEnd, matcher, clearer);
        nodesToRemove.push(...matches);
    }

    return {
        nodesToRemove,
        beforeRange,
        afterRange,
    };
}

function resurroundAdjacent(
    beforeRange: Range,
    afterRange: Range,
    surroundNode: Element,
    base: Element,
    matcher: ElementMatcher,
    clearer: ElementClearer,
): NodesResult {
    const addedNodes: Node[] = [];
    const removedNodes: Node[] = [];

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

    return { addedNodes, removedNodes };
}

/**
 * Avoids splitting existing elements in the surrounded area
 * might create multiple of the surrounding element and remove elements specified by matcher
 * can be used for inline elements e.g. <b>, or <strong>
 * @param range: The range to surround
 * @param surroundNode: This node will be shallowly cloned for surrounding
 * @param base: Surrounding will not ascent beyond this point; base.contains(range.commonAncestorContainer) should be true
 * @param matcher: Used to detect elements will are similar to the surroundNode, and are included in normalization
 * @param clearer: Used to clear elements which have unwanted properties
 **/
export function unsurround(
    range: Range,
    surroundNode: Element,
    base: Element,
    matcher: ElementMatcher = matchTagName(surroundNode.tagName),
    clearer: ElementClearer = () => false,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, matcher);
    const { nodesToRemove, beforeRange, afterRange } = findNodesToRemove(
        range,
        base,
        matcher,
        clearer,
    );

    /**
     * We cannot remove the nodes immediately, because they would make the ranges collapse
     */
    const { addedNodes, removedNodes } = resurroundAdjacent(
        beforeRange,
        afterRange,
        surroundNode,
        base,
        matcher,
        clearer,
    );

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
