// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findFarthest } from "./find-above";
import type { SurroundFormat } from "./match-type";
import { ParseFormat } from "./parse-format";
import { EvaluateFormat } from "./evaluate-format";
import { getRangeAnchors } from "./range-anchors";
import { buildTreeFromNode, splitPartiallySelectedTextNodes } from "./text-node";

export interface NodesResult {
    addedNodes: Node[];
    removedNodes: Node[];
}

export function surround(
    node: Node,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): Range {
    const tree = buildTreeFromNode(node, parseFormat, false);
    console.log('formatting tree', tree);

    tree?.evaluate(evaluateFormat, 0);
    return document.createRange(); // TODO
}

export function reformatRange(
    range: Range,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): Range {
    splitPartiallySelectedTextNodes(range);

    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        parseFormat.base,
        parseFormat.format.matcher,
    );

    if (!farthestMatchingAncestor) {
        return surround(range.commonAncestorContainer, parseFormat, evaluateFormat);
    }

    const tree = buildTreeFromNode(farthestMatchingAncestor.element, parseFormat, true);
    console.log('formatting tree', tree);

    tree?.evaluate(evaluateFormat, 0);
    return document.createRange(); // TODO
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
    // splitPartiallySelectedTextNodes(range);

    const { start, end } = getRangeAnchors(range, format.matcher);
    surround(
        range.commonAncestorContainer,
        ParseFormat.make(format, base, range),
        EvaluateFormat.make(format),
    );

    const surroundedRange = new Range();
    surroundedRange.setStartBefore(start);
    surroundedRange.setEndAfter(end);
    base.normalize();

    return { addedNodes: [], removedNodes: [], surroundedRange };
}
