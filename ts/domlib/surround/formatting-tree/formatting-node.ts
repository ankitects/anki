// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "../child-node-range";
import type { EvaluateFormat } from "../evaluate-format";
import type { ParseFormat } from "../parse-format";
import { MatchNode } from "./match-node";
import { TreeNode } from "./tree-node";

/**
 * Represents a potential insertion point for a tag or, more generally, a point for starting a format procedure.
 */
export class FormattingNode extends TreeNode {
    private constructor(
        public range: ChildNodeRange,
        public covered: boolean,
        public insideRange: boolean,
    ) {
        super(covered, insideRange);
    }

    static make(
        range: ChildNodeRange,
        covered: boolean,
        insideRange: boolean,
    ): FormattingNode {
        return new FormattingNode(range, covered, insideRange);
    }

    /**
     * A merge is combinging two FormattingNodes into a single one.
     *
     * @example
     * Practically speaking, it is what happens, when you combine:
     * `<b>before</b><b>after</b>` into `<b>beforeafter</b>`, or
     * `<b>before</b><img src="image.jpg"><b>after</b>` into
     * `<b>before<img src="image.jpg">after</b>` (negligible nodes inbetween).
     */
    static merge(before: FormattingNode, after: FormattingNode): FormattingNode {
        const node = FormattingNode.make(
            before.range.mergeWith(after.range),
            before.covered && after.covered,
            before.insideRange && after.insideRange,
        );

        node.replaceChildren([...before.children, ...after.children]);
        return node;
    }

    /**
     * An ascent is placing a FormattingNode above a MatchNode
     *
     * @param matchNode: Its children will be discarded in favor of `this`s children.
     *
     * @example
     * Practically speaking, it is what happens, when you turn:
     * `<u><b>inside</b></u>` into `<b><u>inside</u></b>`, or
     * `<u><b>inside</b><img src="image.jpg"></u>` into `<b><u>inside<img src="image.jpg"></u></b>
     */
    ascendAbove(matchNode: MatchNode): void {
        this.range.select(matchNode.element);

        if (!this.hasChildren() && !matchNode.match.type) {
            // Drop matchNode, as it has no effect
            return;
        }

        matchNode.replaceChildren(this.replaceChildren([matchNode]));
    }

    /**
     * Extending only makes sense, if it is following by a FormattingNode
     * ascending above it.
     * Which is why if the match node is not ascendable, we might as well
     * stop extending.
     */
    extendAndAscend(format: ParseFormat): void {
        if (this.length !== 1) {
            return;
        }

        const [only] = this.children;
        if (!(only instanceof MatchNode)) {
            return;
        }

        let top: MatchNode | null = only;
        while (true) {
            if (!(top = top.tryExtend(format))) {
                break;
            }

            if (!format.tryAscend(this, top)) {
                break;
            }
        }
    }

    evaluate(format: EvaluateFormat, leftShift: number): number {
        let innerShift = 0;
        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        this.range.startIndex += leftShift;
        this.range.endIndex += leftShift + innerShift;
        return format.applyFormat(this)
            ? this.range.startIndex - this.range.endIndex + 1
            : 0;
    }
}
