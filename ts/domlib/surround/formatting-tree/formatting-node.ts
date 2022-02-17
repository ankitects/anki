// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FlatRange } from "../flat-range";
import type { EvaluateFormat } from "../format-evaluate";
import type { ParseFormat } from "../format-parse";
import { ElementNode } from "./element-node";
import { TreeNode } from "./tree-node";

/**
 * Represents a potential insertion point for a tag or, more generally, a point for starting a format procedure.
 */
export class FormattingNode extends TreeNode {
    private constructor(
        public range: FlatRange,
        public insideRange: boolean,
        public insideMatch: boolean,
    ) {
        super(insideRange, insideMatch);
    }

    private static make(
        range: FlatRange,
        insideRange: boolean,
        insideMatch: boolean,
    ): FormattingNode {
        return new FormattingNode(range, insideRange, insideMatch);
    }

    static fromText(
        text: Text,
        insideRange: boolean,
        insideMatch: boolean,
    ): FormattingNode {
        return FormattingNode.make(FlatRange.fromNode(text), insideRange, insideMatch);
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
            FlatRange.merge(before.range, after.range),
            before.insideRange && after.insideRange,
            before.insideMatch && after.insideMatch,
        );

        node.replaceChildren(...before.children, ...after.children);
        node.matchLeaves.push(...before.matchLeaves, ...after.matchLeaves);
        node.hasMatchHoles = before.hasMatchHoles || after.hasMatchHoles;

        return node;
    }

    /**
     * An ascent is placing a FormattingNode above a ElementNode.
     * In other terms, if the ElementNode matches, it means that the node creating
     * by `this` during formatting is able to replace the ElementNode semantically.
     *
     * @param elementNode: Its children will be discarded in favor of `this`s children.
     *
     * @example
     * Practically speaking, it is what happens, when you turn:
     * `<u><b>inside</b></u>` into `<b><u>inside</u></b>`, or
     * `<u><b>inside</b><img src="image.jpg"></u>` into `<b><u>inside<img src="image.jpg"></u></b>
     */
    ascendAbove(elementNode: ElementNode): void {
        this.range.select(elementNode.element);

        if (elementNode.match.matches && this.hasMatchHoles) {
            this.matchLeaves.push(elementNode);
            this.hasMatchHoles = false;
        }

        if (!this.hasChildren() && !elementNode.match.matches) {
            // Drop elementNode, as it has no effect
            return;
        }

        elementNode.replaceChildren(...this.replaceChildren(elementNode));
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
        if (!(only instanceof ElementNode)) {
            return;
        }

        let top: ElementNode | null = only;
        while (top) {
            top = top.tryExtend(format);

            if (top && !format.tryAscend(this, top)) {
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

    // The following methods are meant for users when specifying their surround
    // formats and is not vital to the algorithm itself

    /**
     * Match holes are text nodes that are not inside any matches that are
     * descendants of `this` (yet).
     *
     * @see matchLeaves
     */
    hasMatchHoles: boolean = true;

    /**
     * Match leaves are the matching element nodes that are descendants of
     * `this`, and actually affect the text nodes located inside `this`.
     *
     * @see hasMatchHoles
     *
     * @example
     * If we are surrounding with bold, then in this case:
     * `<b><b>first</b><b>second</b></b>
     * The inner b tags are match leaves, but the outer b tag is not, because
     * it does affect any text nodes.
     *
     * @remarks
     * These are important for some ascenders and/or mergers.
     */
    matchLeaves: ElementNode[] = [];

    get firstLeaf(): ElementNode | null {
        if (this.matchLeaves.length === 0) {
            return null;
        }

        return this.matchLeaves[0];
    }

    /**
     * Match ancestors are all matching element nodes that are direct ancestors
     * of `this`
     */
    matchAncestors: ElementNode[] = [];

    get closestAncestor(): ElementNode | null {
        if (this.matchAncestors.length === 0) {
            return null;
        }

        return this.matchAncestors[this.matchAncestors.length - 1];
    }
}
