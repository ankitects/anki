// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ParseFormat } from "../format-parse";
import type { TreeNode } from "../formatting-tree";
import { FormattingNode } from "../formatting-tree";
import { appendNode, insertNode } from "./add-merge";
import { buildFromNode } from "./build";

function mergePreviousTrees(forest: TreeNode[], format: ParseFormat): TreeNode[] {
    const [first, ...tail] = forest;

    if (!(first instanceof FormattingNode)) {
        return forest;
    }

    let merged: TreeNode[] = [first];
    let sibling = first.range.firstChild.previousSibling;

    while (sibling && merged.length === 1) {
        const nodes = buildFromNode(sibling, format, []);

        for (const node of nodes) {
            merged = insertNode(node, merged, format);
        }

        sibling = sibling.previousSibling;
    }

    return [...merged, ...tail];
}

function mergeNextTrees(forest: TreeNode[], format: ParseFormat): TreeNode[] {
    const initial = forest.slice(0, -1);
    const last = forest[forest.length - 1];

    if (!(last instanceof FormattingNode)) {
        return forest;
    }

    let merged: TreeNode[] = [last];
    let sibling = last.range.lastChild.nextSibling;

    while (sibling && merged.length === 1) {
        const nodes = buildFromNode(sibling, format, []);

        for (const node of nodes) {
            merged = appendNode(merged, node, format);
        }

        sibling = sibling.nextSibling;
    }

    return [...initial, ...merged];
}

export function extendAndMerge(forest: TreeNode[], format: ParseFormat): TreeNode[] {
    const merged = mergeNextTrees(mergePreviousTrees(forest, format), format);

    if (merged.length === 1) {
        const [only] = merged;

        if (only instanceof FormattingNode && only.extendAndAscend(format)) {
            return extendAndMerge(merged, format);
        }
    }

    return merged;
}
