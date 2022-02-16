// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { TreeNode } from "./tree-node";

/**
 * Its purpose is to block adjacent Formatting nodes from merging.
 */
export class BlockNode extends TreeNode {
    private constructor(public covered: boolean, public insideRange: boolean) {
        super(covered, insideRange);
    }

    static make(covered: boolean, insideRange: boolean): BlockNode {
        return new BlockNode(covered, insideRange);
    }

    evaluate(): number {
        // noop
        return 0;
    }
}
