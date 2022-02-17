// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../formatting-tree";
import { FormattingNode, ElementNode } from "../formatting-tree";
import type { ParseFormat } from "../format-parse";
import { buildFromNode } from "./build";

interface MergeResult {
    node: FormattingNode;
    hitBorder: boolean;
}

function previousSibling(node: Node): ChildNode | null {
    return node.previousSibling;
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

function innerMerge(
    start: FormattingNode,
    sibling: Node | null,
    format: ParseFormat,
    merge: (before: FormattingNode, after: FormattingNode) => FormattingNode | null,
    getter: (node: Node) => ChildNode | null,
): MergeResult {
    let node = start;

    while (sibling) {
        const siblingNode = buildFromNode(sibling, format, false);

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.insideMatch &&
                siblingNode instanceof FormattingNode &&
                (merged = merge(node, siblingNode))
            ) {
                node = merged;
            } else {
                return {
                    node,
                    hitBorder: false,
                };
            }
        }

        sibling = getter(sibling);
    }

    return {
        node,
        hitBorder: true,
    };
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergePreviousTrees(start: FormattingNode, format: ParseFormat): MergeResult {
    const sibling = previousSibling(start.range.firstChild);
    return innerMerge(
        start,
        sibling,
        format,
        (before: FormattingNode, after: FormattingNode): FormattingNode | null =>
            format.tryMerge(after, before),
        previousSibling,
    );
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergeNextTrees(start: FormattingNode, format: ParseFormat): MergeResult {
    const sibling = nextSibling(start.range.lastChild);
    return innerMerge(
        start,
        sibling,
        format,
        (before: FormattingNode, after: FormattingNode): FormattingNode | null =>
            format.tryMerge(before, after),
        nextSibling,
    );
}

export function extendAndMerge(node: FormattingNode, format: ParseFormat): TreeNode {
    node.extendAndAscend(format);

    const previous = mergePreviousTrees(node, format);
    const next = mergeNextTrees(previous.node, format);
    const parent = node.range.parent.parentElement;

    if (!previous.hitBorder || !next.hitBorder || !parent) {
        return next.node;
    }

    const matchNode = ElementNode.make(
        parent,
        format.createMatch(parent),
        next.node.insideRange,
        next.node.insideMatch,
    );

    format.tryAscend(next.node, matchNode);

    // Even if matchNode ends up matching and next.node refuses to ascend,
    // as this function assumes we're not placed in an ancestor matching
    // element, it does not matter.
    return next.node;
}
