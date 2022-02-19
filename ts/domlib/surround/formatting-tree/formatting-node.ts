// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FlatRange } from "../flat-range";
import type { EvaluateFormat } from "../format-evaluate";
import type { ParseFormat } from "../format-parse";
import type { Match } from "../match-type";
import { ElementNode } from "./element-node";
import { TreeNode } from "./tree-node";

/**
 * Represents a potential insertion point for a tag or, more generally, a point for starting a format procedure.
 */
export class FormattingNode extends TreeNode {
    private constructor(
        public range: FlatRange,
        public insideRange: boolean,
        public matchAncestors: Match[],
    ) {
        super(insideRange, matchAncestors);
    }

    private static make(
        range: FlatRange,
        insideRange: boolean,
        matchAncestors: Match[],
    ): FormattingNode {
        return new FormattingNode(range, insideRange, matchAncestors);
    }

    static fromText(
        text: Text,
        insideRange: boolean,
        matchAncestors: Match[],
    ): FormattingNode {
        return FormattingNode.make(
            FlatRange.fromNode(text),
            insideRange,
            matchAncestors,
        );
    }

    /**
     * A merge is combinging two FormattingNodes into a single one.
     * The merged node will take over their children, their match leaves, and
     * their match holes, but not their extensions.
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
            before.matchAncestors,
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
     * @param elementNode: Non-matching element node. Its children will be
     * discarded in favor of `this`s children.
     *
     * @example
     * Practically speaking, it is what happens, when you turn:
     * `<u><b>inside</b></u>` into `<b><u>inside</u></b>`, or
     * `<u><b>inside</b><img src="image.jpg"></u>` into `<b><u>inside<img src="image.jpg"></u></b>
     */
    ascendAbove(elementNode: ElementNode): void {
        this.range.select(elementNode.element);
        this.extensions.push(elementNode.element as HTMLElement | SVGElement);

        if (!this.hasChildren()) {
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
     *
     * @returns Whether formatting node ascended at least one level
     */
    extendAndAscend(format: ParseFormat): boolean {
        const element = this.range.parent;
        const extension = ElementNode.findExtension(element, this.insideRange, format);

        if (extension && format.tryAscend(this, extension)) {
            return true;
        }

        return false;
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
    matchLeaves: Match[] = [];

    /**
     * Match holes are text nodes that are not inside any matches that are
     * descendants of `this` (yet).
     *
     * @see matchLeaves
     */
    hasMatchHoles = true;

    get firstLeaf(): Match | null {
        if (this.matchLeaves.length === 0) {
            return null;
        }

        return this.matchLeaves[0];
    }

    get closestAncestor(): Match | null {
        if (this.matchAncestors.length === 0) {
            return null;
        }

        return this.matchAncestors[this.matchAncestors.length - 1];
    }

    /**
     * An extension to a formatting node are elements which are directly
     * contained in the formatting node's parent, without any additional
     * non-negligible nodes.
     *
     * @example:
     * When the surround format would only add a class, it could add it to an
     * extension instead:
     * `<span style="color: rgb(255, 0, 0)"><b>inside</b></span>`
     * becomes:
     * `<span class="myclass" style="color: rgb(255, 0, 0)"><b>inside</b></span>`
     */
    extensions: (HTMLElement | SVGElement)[] = [];

    getCache(defaultValue: any): any | null {
        if (this.insideRange) {
            return defaultValue;
        } else if (this.firstLeaf) {
            return this.firstLeaf.cache;
        } else if (this.closestAncestor) {
            return this.closestAncestor.cache;
        }

        // Should never happen, because a formatting node is always either
        // inside a range or inside a match
        return null;
    }
}
