// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findBefore, findAfter } from "./find-adjacent";
import { findWithin, findWithinNode } from "./find-within";
import type { ElementMatcher, ElementClearer } from "./matcher";
import type { ChildNodeRange } from "./child-node-range";

function countChildNodesRespectiveToParent(parent: Node, element: Element): number {
    return element.parentNode === parent ? element.childNodes.length : 1;
}

interface NormalizationResult {
    normalizedRanges: ChildNodeRange[];
    removedNodes: Element[];
}

function normalizeWithinInner(
    node: Element,
    parent: Node,
    removedNodes: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
) {
    const { matches, keepMatches } = findWithinNode(node, matcher);

    for (const found of [
        ...matches,
        ...keepMatches.filter((element) => clearer(element)),
    ]) {
        removedNodes.push(found);
        found.replaceWith(...found.childNodes);
    }

    /**
     * Normalization here is vital so that the
     * original range can selected afterwards
     */
    node.normalize();
    return countChildNodesRespectiveToParent(parent, node);
}

function normalizeAdjacent(
    matches: Element[],
    keepMatches: Element[],
    along: Element[],
    parent: Node,
    removedNodes: Element[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
): [length: number, shift: number] {
    // const { matches, keepMatches, along } = findBefore(normalizedRange, matcher);
    let childCount = along.length;

    for (const match of matches) {
        childCount += normalizeWithinInner(
            match,
            parent,
            removedNodes,
            matcher,
            clearer,
        );

        removedNodes.push(match);
        match.replaceWith(...match.childNodes);
    }

    for (const match of keepMatches) {
        const keepChildCount = normalizeWithinInner(
            match,
            parent,
            removedNodes,
            matcher,
            clearer,
        );

        if (clearer(match)) {
            removedNodes.push(match);
            match.replaceWith(...match.childNodes);
            childCount += keepChildCount;
        } else {
            childCount += 1;
        }
    }

    const length = matches.length + keepMatches.length + along.length;
    const shift = childCount - length;

    return [length, shift];
}

function normalizeWithin(
    matches: Element[],
    keepMatches: Element[],
    parent: Node,
    removedNodes: Element[],
    clearer: ElementClearer,
): number {
    let childCount = 0;

    for (const match of matches) {
        removedNodes.push(match);
        childCount += countChildNodesRespectiveToParent(parent, match);
        match.replaceWith(...match.childNodes);
    }

    for (const match of keepMatches) {
        if (clearer(match)) {
            removedNodes.push(match);
            childCount += countChildNodesRespectiveToParent(parent, match);
            match.replaceWith(...match.childNodes);
        } else {
            childCount += 1;
        }
    }

    const shift = childCount - matches.length - keepMatches.length;
    return shift;
}

export function normalizeInsertionRanges(
    insertionRanges: ChildNodeRange[],
    matcher: ElementMatcher,
    clearer: ElementClearer,
): NormalizationResult {
    const removedNodes: Element[] = [];
    const normalizedRanges: ChildNodeRange[] = [];

    for (const [index, range] of insertionRanges.entries()) {
        const normalizedRange = { ...range };
        const parent = normalizedRange.parent;

        /**
         * This deals with the unnormalized state that would exist
         * after surrounding and finds conflicting elements, for example cases like:
         * `<b>single<b>double</b>single</b>` or `<i><b>before</b></i><b>after</b>`
         */

        if (index === 0) {
            const { matches, keepMatches, along } = findBefore(
                normalizedRange,
                matcher,
            );
            const [length, shift] = normalizeAdjacent(
                matches,
                keepMatches,
                along,
                parent,
                removedNodes,
                matcher,
                clearer,
            );
            normalizedRange.startIndex -= length;
            normalizedRange.endIndex += shift;
        }

        const { matches, keepMatches } = findWithin(normalizedRange, matcher);
        const withinShift = normalizeWithin(
            matches,
            keepMatches,
            parent,
            removedNodes,
            clearer,
        );
        normalizedRange.endIndex += withinShift;

        if (index === insertionRanges.length - 1) {
            const { matches, keepMatches, along } = findAfter(normalizedRange, matcher);
            const [length, shift] = normalizeAdjacent(
                matches,
                keepMatches,
                along,
                parent,
                removedNodes,
                matcher,
                clearer,
            );
            normalizedRange.endIndex += length + shift;
        }

        normalizedRanges.push(normalizedRange);
    }

    return {
        normalizedRanges,
        removedNodes,
    };
}
