// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsEmpty, nodeIsElement } from "../../lib/dom";
import { elementIsBlock } from "../../lib/dom";
import { ascend, isOnlyChild } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import { nodeToChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import type { ElementMatcher } from "./match-type";

function coversWholeParent(childNodeRange: ChildNodeRange): boolean {
    return (
        childNodeRange.startIndex === 0 &&
        childNodeRange.endIndex === childNodeRange.parent.childNodes.length
    );
}

function ascendWhileSingleInline(node: Node, base: Node): Node {
    if (node === base) {
        return node;
    }

    while (
        isOnlyChild(node) &&
        node.parentElement &&
        !elementIsBlock(node.parentElement) &&
        node.parentElement !== base
    ) {
        node = ascend(node);
    }

    return node;
}

function areSiblingChildNodeRanges(
    before: ChildNodeRange,
    after: ChildNodeRange,
): boolean {
    if (before.parent !== after.parent || before.endIndex > after.startIndex) {
        return false;
    }

    if (before.endIndex === after.startIndex) {
        return true;
    }

    for (let index = before.endIndex; index < after.startIndex; index++) {
        const node = before.parent.childNodes[index];

        if (!nodeIsElement(node) || !elementIsEmpty(node)) {
            return false;
        }
    }

    return true;
}

/**
 * @remarks
 * Must be sibling child node ranges
 */
function mergeChildNodeRanges(
    before: ChildNodeRange,
    after: ChildNodeRange,
): ChildNodeRange {
    return {
        parent: before.parent,
        startIndex: before.startIndex,
        endIndex: after.endIndex,
    };
}

function mergeIfSiblings(
    before: ChildNodeRange,
    after: ChildNodeRange,
    base: Element,
): ChildNodeRange | null {
    if (!areSiblingChildNodeRanges(before, after)) {
        return null;
    }

    const merged = mergeChildNodeRanges(before, after);
    const newRange =
        coversWholeParent(merged) && merged.parent !== base
            ? nodeToChildNodeRange(ascendWhileSingleInline(merged.parent, base))
            : merged;

    return newRange;
}

/**
 * @param ranges: Ranges that will be tried to merge into start. Should be in source order.
 * @param start: Range into which ranges are merged
 */
function mergeTillFirstMismatch(
    ranges: ChildNodeRange[],
    start: ChildNodeRange,
    base: Element,
): ChildNodeRange[] {
    const minimized = [start];

    for (let i = ranges.length - 1; i >= 0; i--) {
        const newRange = mergeIfSiblings(ranges[i], minimized[0], base);

        if (newRange) {
            minimized[0] = newRange;
        } else {
            minimized.unshift(...ranges.slice(0, i + 1).reverse());
            break;
        }
    }

    return minimized;
}

/**
 * @param ranges: Ranges to be normalized. Must have non-zero length.
 */
function mergeRanges(
    ranges: ChildNodeRange[],
    base: Element,
    matcher: ElementMatcher,
): ChildNodeRange[] {
    const [first, ...rest] = ranges;
    findBefore(first, matcher);

    let minimized: ChildNodeRange[] = [first];

    for (const range of rest) {
        minimized = mergeTillFirstMismatch(minimized, range, base);
        findBefore(minimized[0], matcher);
    }

    const [last] = minimized.splice(-1, 1);
    findAfter(last, matcher);

    return mergeTillFirstMismatch(minimized, last, base);
}

export function minimalRanges(
    texts: Text[],
    base: Element,
    matcher: ElementMatcher,
): ChildNodeRange[] {
    if (texts.length === 0) {
        return [];
    }

    const ranges = texts
        .map((node: Node): Node => ascendWhileSingleInline(node, base))
        .map(nodeToChildNodeRange);

    return mergeRanges(ranges, base, matcher);
}
