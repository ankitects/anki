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
        private start: Node,
        private end: Node,
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
        const start = node.childNodes[startIndex];
        const end = node.childNodes[endIndex - 1];

        if (!start || !end) {
            throw new RangeError("FlatRange indices out of bounds");
        }

        return new FlatRange(node, start, end);
    }

    /**
     * @remarks
     * Must be sibling flat ranges.
     */
    static merge(before: FlatRange, after: FlatRange): FlatRange {
        return new FlatRange(before.parent, before.start, after.end);
    }

    /**
     * @remarks
     */
    static fromNode(node: Node): FlatRange {
        const parent = ascend(node);
        return new FlatRange(parent, node, node);
    }

    private boundaryChildFromAnchor(anchor: Node): ChildNode {
        let node: Node = anchor;

        while (node.parentNode && node.parentNode !== this.parent) {
            node = node.parentNode;
        }

        if (node.parentNode !== this.parent) {
            throw new Error("FlatRange anchor is no longer within parent");
        }

        return node as ChildNode;
    }

    get firstChild(): ChildNode {
        return this.boundaryChildFromAnchor(this.start);
    }

    get lastChild(): ChildNode {
        return this.boundaryChildFromAnchor(this.end);
    }

    get startIndex(): number {
        return Array.prototype.indexOf.call(this.parent.childNodes, this.firstChild);
    }

    get endIndex(): number {
        return Array.prototype.indexOf.call(this.parent.childNodes, this.lastChild) + 1;
    }

    /**
     * @see `fromNode`
     */
    select(node: Node): void {
        this.parent = ascend(node);
        this.start = node;
        this.end = node;
    }

    rebaseIfParentIs(removedParent: Node, newParent: Node): void {
        if (this.parent !== removedParent) {
            return;
        }

        this.parent = newParent;
    }

    toDOMRange(): Range {
        const range = new Range();
        range.setStart(this.parent, this.startIndex);
        range.setEnd(this.parent, this.endIndex);

        if (range.collapsed) {
            // If the range is collapsed to a single element, move the range inside the element.
            // This prevents putting the surround above the base element.
            const selected = range.commonAncestorContainer.childNodes[range.startOffset];

            if (nodeIsElement(selected)) {
                range.selectNode(selected);
            }
        }

        return range;
    }

    [Symbol.iterator](): Iterator<ChildNode, null, unknown> {
        const last = this.lastChild;
        let current: ChildNode | null = this.firstChild;

        return {
            next(): IteratorResult<ChildNode, null> {
                if (!current) {
                    return { value: null, done: true };
                }

                const value = current;
                current = value === last ? null : (value.nextSibling as ChildNode | null);
                return { value, done: false };
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
