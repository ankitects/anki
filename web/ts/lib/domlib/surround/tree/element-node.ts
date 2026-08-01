// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { TreeNode } from "./tree-node";

export class ElementNode extends TreeNode {
    private constructor(
        public readonly element: Element,
        public readonly insideRange: boolean,
    ) {
        super(insideRange);
    }

    static make(element: Element, insideRange: boolean): ElementNode {
        return new ElementNode(element, insideRange);
    }
}
