// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../tree";
import { FormattingNode } from "../tree";
import type { ApplyFormat } from "./format";

function iterate<T>(node: TreeNode, format: ApplyFormat<T>): void {
    for (const child of node.children) {
        iterate(child, format);
    }

    if (node instanceof FormattingNode) {
        format.applyFormat(node);
    }
}

export function apply<T>(nodes: TreeNode[], format: ApplyFormat<T>): void {
    for (const node of nodes) {
        iterate(node, format);
    }
}

export { ApplyFormat, ReformatApplyFormat, UnsurroundApplyFormat } from "./format";
