// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { TreeNode } from "./tree-node";

/**
 * Its purpose is to block adjacent FormattingNodes from merging, or single
 * FormattingNodes from trying to ascend.
 */
export class BlockNode extends TreeNode {
    private constructor() {
        super(false);
    }

    static make(): BlockNode {
        return new BlockNode();
    }
}
