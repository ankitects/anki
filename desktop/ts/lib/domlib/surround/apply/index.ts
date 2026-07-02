// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../tree";
import { FormattingNode } from "../tree";
import type { ApplyFormat } from "./format";

function iterate<T>(node: TreeNode, format: ApplyFormat<T>, leftShift: number): number {
    let innerShift = 0;

    for (const child of node.children) {
        innerShift += iterate(child, format, innerShift);
    }

    return node instanceof FormattingNode
        ? applyFormat(node, format, leftShift, innerShift)
        : 0;
}

/**
 * @returns Inner shift.
 */
function applyFormat<T>(
    node: FormattingNode<T>,
    format: ApplyFormat<T>,
    leftShift: number,
    innerShift: number,
): number {
    node.range.startIndex += leftShift;
    node.range.endIndex += leftShift + innerShift;

    return format.applyFormat(node)
        ? node.range.startIndex - node.range.endIndex + 1
        : 0;
}

export function apply<T>(nodes: TreeNode[], format: ApplyFormat<T>): void {
    let innerShift = 0;

    for (const node of nodes) {
        innerShift += iterate(node, format, innerShift);
    }
}

export { ApplyFormat, ReformatApplyFormat, UnsurroundApplyFormat } from "./format";
