// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock,nodeIsElement } from "../../../lib/dom";
import type { EvaluateFormat } from "../format-evaluate";
import type { ParseFormat } from "../format-parse";
import type { Match } from "../match-type";
import { TreeNode } from "./tree-node";

export class ElementNode extends TreeNode {
    private constructor(
        public element: Element,
        public match: Match,
        public insideRange: boolean,
        public matchAncestors: Match[],
    ) {
        super(insideRange, matchAncestors);
    }

    static make(
        element: Element,
        match: Match,
        insideRange: boolean,
        matchAncestors: Match[],
    ): ElementNode {
        return new ElementNode(element, match, insideRange, matchAncestors);
    }

    /**
     * An extension is finding elements directly above a ElementNode.
     *
     * @example
     * This helps with additional normalizations, like in the following case:
     * `<b>before</b><u>inside</u><b>after</b>`.
     * If you were to surround "inside" with bold, it would miss the b tags,
     * because they are not directly adjacent.
     *
     * @internal
     */
    static findExtension(
        node: Node,
        insideRange: boolean,
        format: ParseFormat,
    ): ElementNode | null {
        if (!nodeIsElement(node) || !format.mayExtend(node)) {
            return null;
        }

        return ElementNode.make(node, format.createMatch(node), insideRange, []);
    }

    /**
     * @privateRemarks
     * Also need to check via `ParseFormat.prototype.mayAscend`.
     *
     * @return Whether `this` is a viable target for being ascended by a
     * FormattingNode.
     */
    isAscendable(): boolean {
        return !elementIsBlock(this.element);
    }

    evaluate(format: EvaluateFormat): number {
        let innerShift = 0;

        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        return 0;
    }
}
