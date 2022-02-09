// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "./find-above";
import { findWithinNode, findWithinRange } from "./find-within";
import type { ElementMatcher, FoundMatch } from "./matcher";
import { MatchType } from "./matcher";

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
    found: FoundMatch,
    matcher: ElementMatcher,
    condition: (node: Node) => boolean = () => true,
): Element[] {
    const toRemove: Element[] = [];

    for (const { match, element } of findWithinNode(found.element, matcher)) {
        switch (match.type) {
            case MatchType.MATCH:
                if (condition(element)) {
                    toRemove.push(element);
                }
                break;
            case MatchType.CLEAR:
                // order is very important here as `clear` is idempotent!
                if (condition(element) && match.clear(element)) {
                    toRemove.push(element);
                }
                break;
        }
    }

    if (condition(found.element)) {
        switch (found.match.type) {
            case MatchType.MATCH:
                toRemove.push(found.element);
                break;
            case MatchType.CLEAR:
                if (found.match.clear(found.element)) {
                    toRemove.push(found.element);
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

        const matches = findAndClearWithin(aboveEnd, matcher);
        nodesToRemove.push(...matches);
    }

    return {
        nodesToRemove,
        beforeRange,
        afterRange,
    };
}
