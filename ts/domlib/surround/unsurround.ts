// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { findNodesToRemove } from "./find-remove";
import type { SurroundFormat } from "./matcher";
import type { NodesResult, SurroundNoSplittingResult } from "./no-splitting";
import { surround } from "./no-splitting";
import { getRangeAnchors } from "./range-anchors";

function resurroundAdjacent(
    beforeRange: Range,
    afterRange: Range,
    base: Element,
    format: SurroundFormat,
): NodesResult {
    const addedNodes: Node[] = [];
    const removedNodes: Node[] = [];

    for (const range of [beforeRange, afterRange]) {
        if (range.toString().length > 0) {
            const { addedNodes: added, removedNodes: removed } = surround(
                range,
                base,
                format,
            );
            addedNodes.push(...added);
            removedNodes.push(...removed);
        }
    }

    return { addedNodes, removedNodes };
}

/**
 * The counterpart to `surroundNoSplitting`.
 *
 * @param range: The range to unsurround
 * @param base: Surrounding will not ascend beyond this point. `base.contains(range.commonAncestorContainer)` should be true.
 **/
export function unsurround(
    range: Range,
    base: Element,
    format: SurroundFormat,
): SurroundNoSplittingResult {
    const { start, end } = getRangeAnchors(range, format.matcher);
    const { nodesToRemove, beforeRange, afterRange } = findNodesToRemove(
        range,
        base,
        format.matcher,
        format.clearer,
    );

    /* We cannot remove the nodes immediately, because that would make the ranges collapse */

    const { addedNodes, removedNodes } = resurroundAdjacent(
        beforeRange,
        afterRange,
        base,
        format,
    );

    for (const node of nodesToRemove) {
        if (node.isConnected) {
            node.replaceWith(...node.childNodes);
            removedNodes.push(node);
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
