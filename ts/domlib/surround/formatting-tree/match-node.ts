// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock } from "../../../lib/dom";
import { ascend } from "../../../lib/node";
import type { EvaluateFormat } from "../evaluate-format";
import type { Match } from "../match-type";
import { MatchType } from "../match-type";
import type { ParseFormat } from "../parse-format";
import { TreeNode } from "./tree-node";

export class MatchNode extends TreeNode {
    private constructor(
        public element: Element,
        public match: Match,
        public covered: boolean,
        public insideRange: boolean,
    ) {
        super(covered, insideRange);
    }

    static make(
        element: Element,
        match: Match,
        covered: boolean,
        insideRange: boolean,
    ): MatchNode {
        return new MatchNode(element, match, covered, insideRange);
    }

    /**
     * @privateRemarks
     * Also need to check via `ParseFormat.prototype.mayAscend`.
     *
     * @return Whether `this` is a viable target for being ascended by a
     * FormattingNode.
     */
    isAscendable(): boolean {
        return !elementIsBlock(this.element) && (this.covered || this.insideRange);
    }

    /**
     * An extension is finding elements directly above a MatchNode.
     *
     * @example
     * This helps with additional normalizations, like in the following case:
     * `<b>before</b><u>inside</u><b>after</b>`.
     * If you were to surround "inside" with bold, it would miss the b tags,
     * because they are not directly adjacent.
     */
    tryExtend(format: ParseFormat): MatchNode | null {
        if (!format.mayExtend(this.element)) {
            return null;
        }

        const parent = ascend(this.element) as Element;

        if (!parent && elementIsBlock(parent)) {
            return null;
        }

        const parentNode = MatchNode.make(
            parent,
            format.matches(parent),
            this.covered,
            this.insideRange,
        );

        parentNode.replaceChildren([this]);
        return parentNode;
    }

    private remove(format: EvaluateFormat): number {
        const length = this.element.childNodes.length;

        format.announceElementRemoval(this.element);
        this.element.replaceWith(...this.element.childNodes);

        return length - 1;
    }

    evaluate(format: EvaluateFormat): number {
        let innerShift = 0;
        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        switch (this.match.type) {
            case MatchType.REMOVE:
                return this.remove(format);

            case MatchType.CLEAR:
                if (this.match.clear(this.element as any)) {
                    return this.remove(format);
                }
                break;
        }

        return 0;
    }
}
