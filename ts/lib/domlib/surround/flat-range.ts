// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsComment, nodeIsElement, nodeIsText } from "@tslib/dom";
import { ascend } from "@tslib/node";

/**
 * Represents a subset of DOM ranges which can be called with `.surroundContents()`.
 */
export class FlatRange {
    private constructor(
        public parent: Node,
        public startIndex: number,
        public endIndex: number,
    ) {}

    /**
     * The new flat range does not represent the range itself but
     * rather a possible new node that surrounds the boundary points
     * (node, start) till (node, end).
     *
     * @remarks
     * Indices should be >= 0 and startIndex <= endIndex.
     */
    static make(node: Node, startIndex: number, endIndex = startIndex + 1): FlatRange {
        return new FlatRange(node, startIndex, endIndex);
    }

    /**
     * @remarks
     * Must be sibling flat ranges.
     */
    static merge(before: FlatRange, after: FlatRange): FlatRange {
        return FlatRange.make(before.parent, before.startIndex, after.endIndex);
    }

    /**
     * @remarks
     */
    static fromNode(node: Node): FlatRange {
        const parent = ascend(node);
        const index = Array.prototype.indexOf.call(parent.childNodes, node);

        return FlatRange.make(parent, index);
    }

    get firstChild(): ChildNode {
        return this.parent.childNodes[this.startIndex];
    }

    get lastChild(): ChildNode {
        return this.parent.childNodes[this.endIndex - 1];
    }

    /**
     * @see `fromNode`
     */
    select(node: Node): void {
        this.parent = ascend(node);
        this.startIndex = Array.prototype.indexOf.call(this.parent.childNodes, node);
        this.endIndex = this.startIndex + 1;
    }

    toDOMRange(): Range {
        const range = new Range();
        range.setStart(this.parent, this.startIndex);
        range.setEnd(this.parent, this.endIndex);

        // If the range encompasses exactly one child node
        if (this.endIndex - this.startIndex === 1) {
            const selected = this.parent.childNodes[this.startIndex];

            if (nodeIsElement(selected)) {
                if (selected.textContent && selected.textContent.trim().length > 0) {
                    range.selectNodeContents(selected);
                    return range;
                }

                if (range.collapsed) {
                    range.selectNode(selected);
                }
            }
        }

        return range;
    }

    [Symbol.iterator](): Iterator<ChildNode, null, unknown> {
        const parent = this.parent;
        const end = this.endIndex;
        let step = this.startIndex;

        return {
            next(): IteratorResult<ChildNode, null> {
                if (step >= end) {
                    return { value: null, done: true };
                }

                return { value: parent.childNodes[step++], done: false };
            },
        };
    }

    /**
     * @returns Amount of contained nodes
     */
    get length(): number {
        return this.endIndex - this.startIndex;
    }

    toString(): string {
        let output = "";

        for (const node of [...this]) {
            if (nodeIsText(node)) {
                output += node.data;
            } else if (nodeIsComment(node)) {
                output += `<!--${node.data}-->`;
            } else if (nodeIsElement(node)) {
                output += node.outerHTML;
            }
        }

        return output;
    }
}
