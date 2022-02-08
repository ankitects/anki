// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { surroundChildNodeRangeWithNode } from "./child-node-range";
import type { SurroundFormat } from "./matcher";
import { minimalRanges } from "./minimal-ranges";
import { getRangeAnchors } from "./range-anchors";
import { removeAfter, removeBefore } from "./remove-adjacent";
import { removeWithin } from "./remove-within";
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
    const texts = findTextNodesWithin(range.commonAncestorContainer).filter(
        (text: Text): boolean => text.length > 0 && nodeWithinRange(text, range),
    );

    const ranges = minimalRanges(texts, base);
    const removedNodes =
        ranges.length > 0
            ? [
                  /* modifies insertion ranges */
                  ...removeBefore(ranges[0], matcher, clearer),
                  ...ranges.flatMap((range): Element[] =>
                      removeWithin(range, matcher, clearer),
                  ),
                  ...removeAfter(ranges[ranges.length - 1], matcher, clearer),
              ]
            : [];

    const addedNodes: Element[] = [];
    for (const normalized of ranges) {
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
