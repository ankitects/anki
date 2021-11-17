// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRangeAnchors } from "./range-anchors";
import { nodeWithinRange } from "./within-range";
import { findTextNodesWithin } from "./text-node";
import {
    surroundChildNodeRangeWithNode,
    nodeToChildNodeRange,
} from "./child-node-range";
import { mergeMatchChildNodeRanges } from "./merge-match";
import { ascendWhileSingleInline } from "./ascend";
import { normalizeInsertionRanges } from "./normalize-insertion-ranges";
import { matchTagName } from "./matcher";
import type { ElementMatcher, ElementClearer } from "./matcher";

export interface SurroundNoSplittingResult {
    addedNodes: Node[];
    removedNodes: Node[];
    surroundedRange: Range;
}

export function surround(
    range: Range,
    surroundElement: Element,
    base: Element,
    matcher: ElementMatcher,
    clearer: ElementClearer,
) {
    const containedTextNodes = findTextNodesWithin(
        range.commonAncestorContainer,
    ).filter((text: Text): boolean => nodeWithinRange(text, range));

    if (containedTextNodes.length === 0) {
        return {
            addedNodes: [],
            removedNodes: [],
        };
    }

    const containedRanges = containedTextNodes
        .map((node: Node): Node => ascendWhileSingleInline(node, base))
        .map(nodeToChildNodeRange);

    /* First normalization step */
    const insertionRanges = mergeMatchChildNodeRanges(containedRanges, base);

    /* Second normalization step */
    const { normalizedRanges, removedNodes } = normalizeInsertionRanges(
        insertionRanges,
        matcher,
        clearer,
    );

    const addedNodes: Element[] = [];
    for (const normalized of normalizedRanges) {
        const surroundClone = surroundElement.cloneNode(false) as Element;

        surroundChildNodeRangeWithNode(normalized, surroundClone);
        addedNodes.push(surroundClone);
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
 **/
export function surroundNoSplitting(
    range: Range,
    surroundElement: Element,
    base: Element,
    matcher: ElementMatcher = matchTagName(surroundElement.tagName),
    clearer: ElementClearer = () => false,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, matcher);
    const { addedNodes, removedNodes } = surround(
        range,
        surroundElement,
        base,
        matcher,
        clearer,
    );

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return { addedNodes, removedNodes, surroundedRange };
}
