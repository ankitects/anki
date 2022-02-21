// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../tree";
import { FormattingNode } from "../tree";
import type { ApplyFormat } from "./format";

function applyFormat<T>(
    node: TreeNode,
    format: ApplyFormat<T>,
    leftShift: number,
): number {
    let innerShift = 0;

    for (const child of node.children) {
        innerShift += applyFormat(child, format, innerShift);
    }

    if (!(node instanceof FormattingNode)) {
        return 0;
    }

    node.range.startIndex += leftShift;
    node.range.endIndex += leftShift + innerShift;

    return format.applyFormat(node)
        ? node.range.startIndex - node.range.endIndex + 1
        : 0;
}

export function apply<T>(node: TreeNode, format: ApplyFormat<T>): number {
    return applyFormat(node, format, 0);
}

export { ApplyFormat, UnsurroundApplyFormat } from "./format";
