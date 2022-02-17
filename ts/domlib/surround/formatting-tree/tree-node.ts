// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EvaluateFormat } from "../format-evaluate";

function accumulate(accu: number, tree: TreeNode): number {
    return accu + tree.deepLength;
}

export abstract class TreeNode {
    readonly children: TreeNode[] = [];

    protected constructor(
        /**
         * Whether all text nodes within this node are inside the initial DOM range.
         */
        public insideRange: boolean,
        /**
         * Whether all text nodes within this node are preceded by matching ElementNodes.
         * This is important for deciding whether a text node is turned into a
         * FormattingNode or into a BlockNode, if it outside the initial DOM range.
         */
        public insideMatch: boolean,
    ) {}

    /**
     * @returns Children which were replaced.
     */
    replaceChildren(...newChildren: TreeNode[]): TreeNode[] {
        return this.children.splice(0, this.length, ...newChildren);
    }

    hasChildren(): boolean {
        return this.children.length > 0;
    }

    get length(): number {
        return this.children.length;
    }

    get deepLength(): number {
        return this.children.reduce(accumulate, 0);
    }

    into(...path: number[]): TreeNode | null {
        if (path.length === 0) {
            return this;
        }

        const [next, ...rest] = path;

        if (next in this.children) {
            return this.children[next].into(...rest);
        }

        return null;
    }

    /**
     * @param shift: The shift that occured in child nodes of the previous
     * siblings of the DOM node that this tree node represents (left shift).
     *
     * @returns The shift that occured in the children nodes of this
     * node (inner shift).
     *
     * @internal
     */
    abstract evaluate(format: EvaluateFormat, shift: number): number;
}
