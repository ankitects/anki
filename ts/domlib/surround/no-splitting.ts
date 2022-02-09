// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getNodeCoordinates } from "../location";
import { surroundChildNodeRangeWithNode } from "./child-node-range";
import type { SurroundFormat } from "./matcher";
import { minimalRanges } from "./minimal-ranges";
import { getRangeAnchors } from "./range-anchors";
import { removeWithin } from "./remove-within";
import { findTextsWithinRange, validText } from "./text-node";

export interface NodesResult {
    addedNodes: Node[];
    removedNodes: Node[];
}

export function surround(
    range: Range,
    base: Element,
    { matcher, surroundElement }: SurroundFormat,
): NodesResult {
    const texts = findTextsWithinRange(range).filter(validText);
    const ranges = minimalRanges(texts, base, matcher);

    const removed: Element[] = [];
    const added: Element[] = [];

    for (const range of ranges) {
        removed.push(
            /* modifies ranges */
            ...removeWithin(range, matcher),
        );

        const surroundClone = surroundElement.cloneNode(false) as Element;

        surroundChildNodeRangeWithNode(range, surroundClone);
        added.push(surroundClone);
    }

    return { addedNodes: added, removedNodes: removed };
}

export type SurroundNoSplittingResult = NodesResult & {
    surroundedRange: Range;
};

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
    // TODO
    // const coords = [
    //     getNodeCoordinates(range.startContainer, base),
    //     getNodeCoordinates(range.endContainer, base),
    // ];

    const { start, end } = getRangeAnchors(range, format.matcher);
    const { addedNodes, removedNodes } = surround(range, base, format);

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return { addedNodes, removedNodes, surroundedRange };
}
