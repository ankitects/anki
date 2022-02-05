// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ascendWhileSingleInline } from "./ascend";
import {
    nodeToChildNodeRange,
    surroundChildNodeRangeWithNode,
} from "./child-node-range";
import type { SurroundFormat } from "./matcher";
import { mergeMatchChildNodeRanges } from "./merge-match";
import { normalizeInsertionRanges } from "./normalize-insertion-ranges";
import { getRangeAnchors } from "./range-anchors";
import { findTextNodesWithin } from "./text-node";
import { nodeWithinRange } from "./within-range";

export interface NodesResult {
    addedNodes: Node[];
    removedNodes: Node[];
}

export type SurroundNoSplittingResult = NodesResult & {
    surroundedRange: Range;
};

export function surround(
    range: Range,
    base: Element,
    { matcher, clearer, surroundElement }: SurroundFormat,
): NodesResult {
    const containedTextNodes = findTextNodesWithin(
        range.commonAncestorContainer,
    ).filter((text: Text): boolean => text.length > 0 && nodeWithinRange(text, range));

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
 * Avoids splitting existing elements in the surrounded area. Might create
 * multiple of the surrounding element and remove elements specified by matcher.
 * Can be used for inline elements e.g. <b>, or <strong>.
 *
 * @param range: The range to surround
 * @param base: Surrounding will not ascent beyond this point; base.contains(range.commonAncestorContainer) should be true
 * @param format: Specifies the type of surrounding.
 **/
export function surroundNoSplitting(
    range: Range,
    base: Element,
    format: SurroundFormat,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, format.matcher);
    const { addedNodes, removedNodes } = surround(range, base, format);

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return { addedNodes, removedNodes, surroundedRange };
}
