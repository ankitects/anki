// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { TreeNode } from "../tree";
import { FormattingNode } from "../tree";
import type { BuildFormat } from "./format";

function mergeAppendNode<T>(
    initial: TreeNode[],
    last: FormattingNode<T>,
    format: BuildFormat<T>,
): TreeNode[] {
    const minimized: TreeNode[] = [last];

    for (let i = initial.length - 1; i >= 0; i--) {
        const next = initial[i];

        let merged: FormattingNode<T> | null;
        if (next instanceof FormattingNode && (merged = format.tryMerge(next, last))) {
            minimized[0] = merged;
        } else {
            minimized.unshift(...initial.slice(0, i + 1));
            break;
        }
    }

    return minimized;
}

/**
 * Tries to merge `last`, into the end of `initial`.
 */
export function appendNode<T>(
    initial: TreeNode[],
    last: TreeNode,
    format: BuildFormat<T>,
): TreeNode[] {
    if (last instanceof FormattingNode) {
        return mergeAppendNode(initial, last, format);
    } else {
        return [...initial, last];
    }
}

function mergeInsertNode<T>(
    first: FormattingNode<T>,
    tail: TreeNode[],
    format: BuildFormat<T>,
): TreeNode[] {
    const minimized: TreeNode[] = [first];

    for (let i = 0; i <= tail.length; i++) {
        const next = tail[i];

        let merged: FormattingNode<T> | null;
        if (next instanceof FormattingNode && (merged = format.tryMerge(first, next))) {
            minimized[0] = merged;
        } else {
            minimized.push(...tail.slice(i));
            break;
        }
    }

    return minimized;
}

/**
 * Tries to merge `first`, into the start of `tail`.
 */
export function insertNode<T>(
    first: TreeNode,
    tail: TreeNode[],
    format: BuildFormat<T>,
): TreeNode[] {
    if (first instanceof FormattingNode) {
        return mergeInsertNode(first, tail, format);
    } else {
        return [first, ...tail];
    }
}
