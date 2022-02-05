// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "./find-above";
import { findWithinNode, findWithinRange } from "./find-within";
import type {
    SurroundFormat,
    ElementClearer,
    ElementMatcher,
    FoundMatch,
} from "./matcher";
import { MatchResult } from "./matcher";
import type { NodesResult, SurroundNoSplittingResult } from "./no-splitting";
import { surround } from "./no-splitting";
import { getRangeAnchors } from "./range-anchors";

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
    base: Element,
    format: SurroundFormat,
): NodesResult {
    const addedNodes: Node[] = [];
    const removedNodes: Node[] = [];

    if (beforeRange.toString().length > 0) {
        const { addedNodes: added, removedNodes: removed } = surround(
            beforeRange,
            base,
            format,
        );
        addedNodes.push(...added);
        removedNodes.push(...removed);
    }

    if (afterRange.toString().length > 0) {
        const { addedNodes: added, removedNodes: removed } = surround(
            afterRange,
            base,
            format,
        );
        addedNodes.push(...added);
        removedNodes.push(...removed);
    }

    return { addedNodes, removedNodes };
}

/**
 * The counterpart to `surroundNoSplitting`.
 *
 * @param range: The range to unsurround
 * @param base: Surrounding will not ascend beyond this point. `base.contains(range.commonAncestorContainer)` should be true.
 **/
export function unsurround(
    range: Range,
    base: Element,
    format: SurroundFormat,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, format.matcher);
    const { nodesToRemove, beforeRange, afterRange } = findNodesToRemove(
        range,
        base,
        format.matcher,
        format.clearer,
    );

    /* We cannot remove the nodes immediately, because they would make the ranges collapse */

    const { addedNodes, removedNodes } = resurroundAdjacent(
        beforeRange,
        afterRange,
        base,
        format,
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
