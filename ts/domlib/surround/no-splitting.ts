// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "./find-above";
import type { SurroundFormat } from "./match-type";
import { minimalRanges } from "./minimal-ranges";
import { getRangeAnchors } from "./range-anchors";
import { removeWithin } from "./remove-within";
import {
    buildFormattingTree,
    buildTreeFromRange,
    findTextsWithinNode,
    findTextsWithinRange,
    validText,
} from "./text-node";

export interface NodesResult {
    addedNodes: Node[];
    removedNodes: Node[];
}

export function surround(
    range: Range,
    base: Element,
    format: SurroundFormat,
): NodesResult {
    // TODO maybe offer those as two functions, one finds within range, one
    // within farthestMatchingAncestor.
    // If you're surrounding in a matching ancestor, the operation is more
    // akin to a reformatting/normalization, than actually surrounding.
    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        base,
        format.matcher,
    );

    const tree = farthestMatchingAncestor
        ? buildFormattingTree(
              farthestMatchingAncestor.element,
              range,
              format.matcher,
              true,
              base,
          )
        : buildTreeFromRange(range, format.matcher, base);

    tree?.evaluate(format, 0);
    console.log("formatting tree", tree);

    // const allTexts = farthestMatchingAncestor
    //     ? findTextsWithinNode(farthestMatchingAncestor.element)
    //     : findTextsWithinRange(range);

    // const texts = allTexts.filter(validText).filter(validText);
    // const ranges = minimalRanges(texts, base, matcher);
    // console.log("result", ranges);

    // const removed: Element[] = [];
    // const added: Element[] = [];

    // for (const range of ranges) {
    //     removed.push(
    //         /* modifies ranges */
    //         ...removeWithin(range, matcher),
    //     );

    //     const surroundClone = surroundElement.cloneNode(false) as Element;

    //     range.surroundWithNode(surroundClone);
    //     added.push(surroundClone);
    // }

    // return { addedNodes: added, removedNodes: removed };
    return { addedNodes: [], removedNodes: [] };
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

    const { start, end } = getRangeAnchors(range, format.matcher);
    const { addedNodes, removedNodes } = surround(range, base, format);

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return { addedNodes, removedNodes, surroundedRange };
}
