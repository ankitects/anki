// Copyright: Ankitects Pty Ltd and contributors
import { elementIsBlock } from "../../lib/dom";
import { ascend } from "../../lib/node";
import type { ChildNodeRange } from "./child-node-range";
import type { Match } from "./match-type";
import type { ElementMatcher, SurroundFormat } from "./match-type";
import { applyClearer, applyMatcher, MatchType } from "./match-type";
import { nodeIsAmongNegligibles } from "./node-negligible";

// class ShallowTreeIterator {
//     constructor(
//         private tree: MatchTree,
//         private index: number = 0,
//         private end: number = 0,
//     ) {}

//     next() {
//         if (this.end >= this.tree.length || this.index >= this.end) {
//             return { done: true };
//         }

//         this.index;
//         return { done: true };
//     }
// }

function accumulate(accu: number, tree: TreeNode): number {
    return accu + tree.deepLength;
}

export abstract class TreeNode {
    readonly children: TreeNode[] = [];

    protected constructor(
        /**
         * Whether all text nodes within this node are covered by matching MatchNode.
         */
        public covered: boolean,
        /**
         * Whether all text nodes within this node are inside the initial range.
         */
        public insideRange: boolean,
    ) {}

    /**
     * @returns Children which were replaced.
     */
    replaceChildren(newChildren: TreeNode[]): TreeNode[] {
        return this.children.splice(0, this.length, ...newChildren);
    }

    hasChildren(): boolean {
        return this.children.length > 0;
    }

    get length(): number {
        return this.children.length;
    }

    get deepLength(): number {
        return this.children.reduce(accumulate, 0);
    }

    into(...path: number[]): TreeNode | null {
        if (path.length === 0) {
            return this;
        }

        const [next, ...rest] = path;

        if (next in this.children) {
            return this.children[next].into(...rest);
        }

        return null;
    }

    /**
     * The DOM node that this node represents
     */
    // abstract get element(): Element;

    /**
     * @param shift: The shift that occured in child nodes of the previous
     * siblings of the DOM node that this tree node represents (left shift).
     *
     * @returns The shift that occured in the children nodes of this
     * node (inner shift).
     */
    abstract evaluate(format: SurroundFormat, shift: number): number;

    // [Symbol.iterator]() {
    //     return new ShallowTreeIterator(this);
    // }
}

/**
 * Its purpose is to block adjacent Formatting nodes from merging.
 */
export class BlockNode extends TreeNode {
    private constructor(
        public covered: boolean,
        public insideRange: boolean,
    ) {
        super(covered, insideRange);
    }

    static make(
        covered: boolean,
        insideRange: boolean,
    ): BlockNode {
        return new BlockNode(covered, insideRange);
    }

    evaluate(): number {
        // noop
        return 0;
    }
}

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
     * An extension is finding elements directly above a MatchNode.
     *
     * @example
     * This helps finding additional normalizations, like in the following:
     * `<b>before</b><u>inside</u><b>after</b>`.
     * If you were to surround `inside`, it would miss the b tags, because they
     * are not directly adjacent.
     */
    tryExtend(base: Node, matcher: ElementMatcher): MatchNode | null {
        if (this.element === base || !nodeIsAmongNegligibles(this.element)) {
            return null;
        }

        const parent = ascend(this.element) as Element;

        if (!parent && elementIsBlock(parent)) {
            return null;
        }

        const parentNode = MatchNode.make(
            parent,
            applyMatcher(matcher, parent),
            this.covered,
            this.insideRange,
        );

        parentNode.replaceChildren([this]);
        return parentNode;
    }

    evaluate(format: SurroundFormat): number {
        let innerShift = 0;
        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        switch (this.match.type) {
            case MatchType.REMOVE:
                const length = this.element.childNodes.length;
                this.element.replaceWith(...this.element.childNodes);
                return length - 1;

            case MatchType.CLEAR:
                const shouldRemove = applyClearer(this.match.clear, this.element);
                if (shouldRemove) {
                    this.element.replaceWith(...this.element.childNodes);
                }
                break;
        }

        return innerShift;
    }
}

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
        insideRange: boolean,
        covered: boolean,
    ): FormattingNode {
        return new FormattingNode(range, insideRange, covered);
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
            before.insideRange && after.insideRange,
            before.covered && after.covered,
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
     * `<u><b>inside</b><img src="image.jpg"></u>` into `<b><u>inside<img src="image.jpg"></b>
     */
    private ascendAbove(matchNode: MatchNode): void {
        this.range.select(matchNode.element);

        if (!this.hasChildren() && !matchNode.match.type) {
            // Drop matchNode, as it has no effect
            return;
        }

        matchNode.replaceChildren(this.replaceChildren([matchNode]));
    }

    tryAscend(matchNode: MatchNode, base: Element): boolean {
        if (
            elementIsBlock(matchNode.element) ||
            matchNode.element === base ||
            !(matchNode.covered || matchNode.insideRange)
        ) {
            return false;
        }

        this.ascendAbove(matchNode);
        return true;
    }

    /**
     * Extending `MatchNode` only makes sense, if it is following by a
     * FormattingNode ascending above it.
     * Which is why if the formatting node refuses to ascend, we might as well
     * stop extending.
     */
    extendAndAscend(matcher: ElementMatcher, base: Element): void {
        if (this.length !== 1) {
            return;
        }

        const [matchNode] = this.children;
        if (!(matchNode instanceof MatchNode)) {
            return;
        }

        let top = matchNode.tryExtend(base, matcher);

        while (top) {
            if (!this.tryAscend(top, base)) {
                break;
            }

            top = top.tryExtend(base, matcher);
        }
    }

    evaluate(format: SurroundFormat, leftShift: number): number {
        let innerShift = 0;
        for (const child of this.children) {
            innerShift += child.evaluate(format, innerShift);
        }

        this.range.startIndex += leftShift;
        this.range.endIndex += leftShift + innerShift;
        format.formatter(this.range.toDOMRange());

        return this.range.startIndex - this.range.endIndex + 1;
    }
}
