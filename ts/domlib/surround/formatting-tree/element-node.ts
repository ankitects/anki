// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Match } from "../match-type";
import { TreeNode } from "./tree-node";

export class ElementNode extends TreeNode {
    private constructor(
        public readonly element: Element,
        public readonly insideRange: boolean,
        public readonly matchAncestors: Match[],
    ) {
        super(insideRange, matchAncestors);
    }

    static make(
        element: Element,
        insideRange: boolean,
        matchAncestors: Match[] = [],
    ): ElementNode {
        return new ElementNode(element, insideRange, matchAncestors);
    }
}
