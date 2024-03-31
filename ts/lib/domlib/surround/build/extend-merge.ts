// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { BuildFormat } from "../build";
import type { TreeNode } from "../tree";
import { FormattingNode } from "../tree";
import { appendNode, insertNode } from "./add-merge";
import { buildFromNode } from "./build-tree";

function mergePreviousTrees<T>(forest: TreeNode[], format: BuildFormat<T>): TreeNode[] {
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

function mergeNextTrees<T>(forest: TreeNode[], format: BuildFormat<T>): TreeNode[] {
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

export function extendAndMerge<T>(
    forest: TreeNode[],
    format: BuildFormat<T>,
): TreeNode[] {
    const merged = mergeNextTrees(mergePreviousTrees(forest, format), format);

    if (merged.length === 1) {
        const [only] = merged;

        if (only instanceof FormattingNode) {
            const elementNode = only.getExtension();

            if (elementNode && format.tryAscend(only, elementNode)) {
                return extendAndMerge(merged, format);
            }
        }
    }

    return merged;
}
