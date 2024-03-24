// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export abstract class TreeNode {
    readonly children: TreeNode[] = [];

    protected constructor(
        /**
         * Whether all text nodes within this node are inside the initial DOM range.
         */
        public insideRange: boolean,
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
}
