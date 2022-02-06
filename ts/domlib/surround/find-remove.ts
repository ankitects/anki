// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "./find-above";
import { findWithinNode, findWithinRange } from "./find-within";
import type { ElementClearer, ElementMatcher, FoundMatch } from "./matcher";
import { MatchResult } from "./matcher";

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
    /**
     * starts to the farthest any of the nodes to be removed
     * extend in start direction till the start of the original range
     */
    beforeRange: Range;
    /**
     * will start at the end of the original range and will extend as
     * far as any of the nodes to be removed extend in end direction
     */
    afterRange: Range;
}

export function findNodesToRemove(
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
