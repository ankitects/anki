// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { buildFromNode } from "./build-tree";
import { EvaluateFormat } from "./evaluate-format";
import { extendAndMerge } from "./extend-merge";
import { findFarthest } from "./find-above";
import type { SurroundFormat } from "./match-type";
import { ParseFormat } from "./parse-format";
import { getRangeAnchors } from "./range-anchors";
import { splitPartiallySelected } from "./text-node";
import { FormattingNode } from "./tree-node";

function build(
    node: Node,
    format: ParseFormat,
    evaluateFormat: EvaluateFormat,
    covered: boolean,
) {
    let output = buildFromNode(node, format, covered);

    if (output instanceof FormattingNode) {
        output = extendAndMerge(output, format);
    }

    output?.evaluate(evaluateFormat, 0);
    return document.createRange(); // TODO
}

export interface NodesResult {
    addedNodes: Node[];
    removedNodes: Node[];
}

export function surround(
    node: Node,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): Range {
    return build(node, parseFormat, evaluateFormat, false);
}

export function reformatRange(
    range: Range,
    parseFormat: ParseFormat,
    evaluateFormat: EvaluateFormat,
): Range {
    splitPartiallySelected(range);

    const farthestMatchingAncestor = findFarthest(
        range.commonAncestorContainer,
        parseFormat.base,
        parseFormat.format.matcher,
    );

    if (!farthestMatchingAncestor) {
        return surround(range.commonAncestorContainer, parseFormat, evaluateFormat);
    }

    return build(farthestMatchingAncestor.element, parseFormat, evaluateFormat, true);
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
