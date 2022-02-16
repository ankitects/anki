// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../formatting-tree";
import { FormattingNode, MatchNode } from "../formatting-tree";
import type { ParseFormat } from "../parse-format";
import { buildFromNode } from "./build";

function previousSibling(node: Node): ChildNode | null {
    return node.previousSibling;
}

function nextSibling(node: Node): ChildNode | null {
    return node.nextSibling;
}

interface MergeResult {
    node: FormattingNode;
    hitBorder: boolean;
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergePreviousTrees(start: FormattingNode, format: ParseFormat): MergeResult {
    let result = start;

    let sibling = previousSibling(start.range.parent);
    while (sibling) {
        const siblingNode = buildFromNode(sibling, format, false);

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = format.tryMerge(siblingNode, result))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                };
            }
        }

        sibling = previousSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    };
}

/**
 * @param main: Node into which is merged. Is modified.
 *
 * @returns Whether siblings were exhausted during merging
 */
function mergeNextTrees(start: FormattingNode, format: ParseFormat): MergeResult {
    let result = start;

    let sibling = nextSibling(start.range.parent);
    while (sibling) {
        const siblingNode = buildFromNode(sibling, format, false);

        if (siblingNode) {
            let merged: FormattingNode | null;
            if (
                siblingNode.covered &&
                siblingNode instanceof FormattingNode &&
                (merged = format.tryMerge(result, siblingNode))
            ) {
                result = merged;
            } else {
                return {
                    node: result,
                    hitBorder: false,
                };
            }
        }

        sibling = nextSibling(sibling);
    }

    return {
        node: result,
        hitBorder: true,
    };
}

export function extendAndMerge(node: FormattingNode, format: ParseFormat): TreeNode {
    node.extendAndAscend(format);

    const previous = mergePreviousTrees(node, format);
    const next = mergeNextTrees(previous.node, format);
    const parent = node.range.parent.parentElement;

    if (!previous.hitBorder || !next.hitBorder || !parent) {
        return next.node;
    }

    const matchNode = MatchNode.make(
        parent,
        format.matches(parent),
        next.node.covered,
        next.node.insideRange,
    );

    format.tryAscend(next.node, matchNode);

    // Even if matchNode ends up matching and next.node refuses to ascend,
    // as this function assumes we're not placed in an ancestor matching
    // element, it does not matter.
    return next.node;
}
