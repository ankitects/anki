// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "@tslib/dom";

import { FlatRange } from "../flat-range";
import type { Match } from "../match-type";
import { ElementNode } from "./element-node";
import { TreeNode } from "./tree-node";

/**
 * Represents a potential insertion point for a tag or, more generally, a point for starting a format procedure.
 */
export class FormattingNode<T = never> extends TreeNode {
    private constructor(
        public readonly range: FlatRange,
        public readonly insideRange: boolean,
        /**
         * Match ancestors are all matching matches that are direct ancestors
         * of `this`. This is important for deciding whether a text node is
         * turned into a FormattingNode or into a BlockNode, if it is outside
         * the initial DOM range.
         */
        public readonly matchAncestors: Match<T>[],
    ) {
        super(insideRange);
    }

    private static make<T>(
        range: FlatRange,
        insideRange: boolean,
        matchAncestors: Match<T>[],
    ): FormattingNode<T> {
        return new FormattingNode(range, insideRange, matchAncestors);
    }

    static fromText<T>(
        text: Text,
        insideRange: boolean,
        matchAncestors: Match<T>[],
    ): FormattingNode<T> {
        return FormattingNode.make(
            FlatRange.fromNode(text),
            insideRange,
            matchAncestors,
        );
    }

    /**
     * A merge is combinging two formatting nodes into a single one.
     * The merged node will take over their children, their match leaves, and
     * their match holes, but will drop their extensions.
     *
     * @example
     * Practically speaking, it is what happens, when you combine:
     * `<b>before</b><b>after</b>` into `<b>beforeafter</b>`, or
     * `<b>before</b><img src="image.jpg"><b>after</b>` into
     * `<b>before<img src="image.jpg">after</b>` (negligible nodes inbetween).
     */
    static merge<T>(
        before: FormattingNode<T>,
        after: FormattingNode<T>,
    ): FormattingNode<T> {
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

    toString(): string {
        return this.range.toString();
    }

    /**
     * An ascent is placing a FormattingNode above an ElementNode.
     * This happens, when the element node is an extension to the formatting node.
     *
     * @param elementNode: Its children will be discarded in favor of `this`s
     * children.
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
    getExtension(): ElementNode | null {
        const node = this.range.parent;

        if (nodeIsElement(node)) {
            return ElementNode.make(node, this.insideRange);
        }

        return null;
    }

    // The following methods are meant for users when specifying their surround
    // formats and is not vital to the algorithm itself

    /**
     * Match leaves are the matching elements that are/were descendants of
     * `this`. This makes them the element nodes, which actually affect text
     * nodes located inside `this`.
     *
     * @example
     * If we are surrounding with bold, then in this case:
     * `<b><b>first</b><b>second</b></b>
     * The inner b tags are match leaves, but the outer b tag is not, because
     * it does affect any text nodes.
     *
     * @remarks
     * These are important for mergers.
     */
    matchLeaves: Match<T>[] = [];

    get firstLeaf(): Match<T> | null {
        if (this.matchLeaves.length === 0) {
            return null;
        }

        return this.matchLeaves[0];
    }

    /**
     * Match holes are text nodes which are descendants of `this`, but are not
     * descendants of any match leaves of `this`.
     */
    hasMatchHoles = true;

    get closestAncestor(): Match<T> | null {
        if (this.matchAncestors.length === 0) {
            return null;
        }

        return this.matchAncestors[this.matchAncestors.length - 1];
    }

    /**
     * Extensions of formatting nodes with a single element contained in their
     * range are direct exclusive descendant elements of this element.
     * Extensions are sorted in tree order.
     *
     * @example
     * When surrounding "inside" with a bold format in the following case:
     * `<span class="myclass"><em>inside</em></span>`
     * The formatting node would sit above the span (it ascends above both
     * the em and the span tag), and its extensions are the span tag and the
     * em tag (in this order).
     *
     * @example
     * When a format only wants to add a class, it would typically look for an
     * extension first. When applying class="myclass" to "inside" in the
     * following case:
     * `<em><span style="color: rgb(255, 0, 0)"><b>inside</b></span></em>`
     * It should typically become:
     * `<em><span class="myclass" style="color: rgb(255, 0, 0)"><b>inside</b></span></em>`
     */
    extensions: (HTMLElement | SVGElement)[] = [];

    /**
     * @param insideValue: The value that should be returned, if the formatting
     * node is inside the original range. If the node is not inside the original
     * range, the cache of the first leaf, or the closest match ancestor will be
     * returned.
     */
    getCache(insideValue: T): T | null {
        if (this.insideRange) {
            return insideValue;
        } else if (this.firstLeaf) {
            return this.firstLeaf.cache;
        } else if (this.closestAncestor) {
            return this.closestAncestor.cache;
        }

        // Should never happen, as a formatting node is always either
        // inside a range or inside a match
        return null;
    }

    /**
     * Whether the text nodes in this formatting node are affected by any match.
     * This can only be false, if `insideRange` is true (otherwise it would have
     * become a BlockNode).
     */
    get hasMatch(): boolean {
        return this.matchLeaves.length > 0 || this.matchAncestors.length > 0;
    }
}
