// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock } from "../../../lib/dom";
import { ascend } from "../../../lib/node";
import type { EvaluateFormat } from "../format-evaluate";
import type { Match } from "../match-type";
import type { ParseFormat } from "../format-parse";
import { TreeNode } from "./tree-node";

export class ElementNode extends TreeNode {
    private constructor(
        public element: Element,
        public match: Match,
        public insideRange: boolean,
        public insideMatch: boolean,
    ) {
        super(insideRange, insideMatch);
    }

    static make(
        element: Element,
        match: Match,
        insideRange: boolean,
        insideMatch: boolean,
    ): ElementNode {
        return new ElementNode(element, match, insideRange, insideMatch);
    }

    /**
     * @privateRemarks
     * Also need to check via `ParseFormat.prototype.mayAscend`.
     *
     * @return Whether `this` is a viable target for being ascended by a
     * FormattingNode.
     */
    isAscendable(): boolean {
        return !elementIsBlock(this.element) && (this.insideRange || this.insideMatch);
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
    tryExtend(format: ParseFormat): ElementNode | null {
        if (!format.mayExtend(this.element)) {
            return null;
        }

        const parent = ascend(this.element) as Element;

        if (!parent && elementIsBlock(parent)) {
            return null;
        }

        const parentNode = ElementNode.make(
            parent,
            format.createMatch(parent),
            this.insideRange,
            this.insideMatch,
        );

        parentNode.replaceChildren(this);
        return parentNode;
    }

    /**
     * Returns the change in nodes after removal of `this.element`.
     */
    private remove(): number {
        const length = this.element.childNodes.length;
        this.element.replaceWith(...this.element.childNodes);
        return length - 1;
    }

    evaluate(format: EvaluateFormat): number {
        let innerShift = 0;
        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        if (this.match.markedClear) {
            this.match.clearCallback!();
        }

        if (this.match.markedRemove) {
            format.announceElementRemoval(this.element);
            return this.remove();
        }

        return 0;
    }
}
