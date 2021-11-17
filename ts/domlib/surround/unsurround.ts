// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRangeAnchors } from "./range-anchors";
import type { SurroundNoSplittingResult } from "./no-splitting";
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
    const aboveEnd = findFarthest(range.endContainer, base, matcher);
    const between = findBetween(range, matcher, aboveStart?.element, aboveEnd?.element);

    const beforeRange = new Range();
    beforeRange.setEnd(range.startContainer, range.startOffset);
    beforeRange.collapse(false);

    if (aboveStart) {
        beforeRange.setStartBefore(aboveStart.element);

        function prohibitOverlapse(node: Node): boolean {
            /* otherwise, they will be added to nodesToRemove twice
             * and will also be cleared twice */
            return (
                !node.contains(range.endContainer) && !range.endContainer.contains(node)
            );
        }

        const matches = findAndClearWithin(
            aboveStart,
            matcher,
            clearer,
            prohibitOverlapse,
        );
        nodesToRemove.push(...matches);
    }

    for (const { element: found } of between) {
        removedNodes.push(found);
        found.replaceWith(...found.childNodes);
    }

    const afterRange = new Range();
    afterRange.setStart(range.endContainer, range.endOffset);
    afterRange.collapse(true);

    if (aboveEnd) {
        afterRange.setEndAfter(aboveEnd.element);

        const matches = findAndClearWithin(aboveEnd, matcher, clearer);
        nodesToRemove.push(...matches);
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
