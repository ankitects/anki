// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "./formatting-tree";
import { FormattingNode } from "./formatting-tree";
import type { ParseFormat } from "./parse-format";

/**
 * @param last: The node which is intended to be the new end node.
 */
function mergeInNode(
    initial: TreeNode[],
    last: FormattingNode,
    format: ParseFormat,
): TreeNode[] {
    const minimized: TreeNode[] = [last];

    for (let i = initial.length - 1; i >= 0; i--) {
        const next = initial[i];

        let merged: FormattingNode | null;
        if (next instanceof FormattingNode && (merged = format.tryMerge(next, last))) {
            minimized[0] = merged;
        } else {
            minimized.unshift(...initial.slice(0, i + 1));
            break;
        }
    }

    return minimized;
}

export function appendNode(
    nodes: TreeNode[],
    node: TreeNode,
    format: ParseFormat,
): TreeNode[] {
    if (node instanceof FormattingNode) {
        return mergeInNode(nodes, node, format);
    } else {
        return [...nodes, node];
    }
}
