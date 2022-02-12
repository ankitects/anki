// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import type { Match } from "./match-type";

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
    readonly children: TreeNode[] =[];

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

    // [Symbol.iterator]() {
    //     return new ShallowTreeIterator(this);
    // }
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

    static make(element: Element, match: Match, covered: boolean, insideRange: boolean): MatchNode {
        return new MatchNode(element, match, covered, insideRange);
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
    ascendAbove(matchNode: MatchNode): void {
        this.range.select(matchNode.element);

        if (!this.hasChildren() && !matchNode.match.type) {
            // Drop matchNode, as it has no effect
            return;
        }

        matchNode.replaceChildren(this.replaceChildren([matchNode]))
    }
}
