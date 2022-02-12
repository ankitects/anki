// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import { ascend } from "../../lib/node";

export class ChildNodeRange {
    private constructor(
        public parent: Node,
        public startIndex: number,
        public endIndex: number,
    ) {}

    /**
     * @remarks
     * Indices should be >= 0 and startIndex < endIndex
     */
    static make(
        node: Node,
        startIndex: number,
        endIndex = startIndex + 1,
    ): ChildNodeRange {
        return new ChildNodeRange(node, startIndex, endIndex);
    }

    /**
     * @remarks
     * The new child node range might not necessarily indicate the node itself but
     * could also be a supposed new node that entirely surrounds the passed in node
     */
    static fromNode(node: Node): ChildNodeRange {
        const parent = ascend(node);
        const index = Array.prototype.indexOf.call(parent.childNodes, node);

        return ChildNodeRange.make(parent, index);
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

        return range;
    }

    surroundWithNode(node: Node): void {
        const range = this.toDOMRange();

        if (range.collapsed) {
            // If the range is collapsed to a single element, move the range inside the element.
            // This prevents putting the surround above the base element.
            const selected =
                range.commonAncestorContainer.childNodes[range.startOffset];

            if (nodeIsElement(selected)) {
                range.selectNode(selected);
            }
        }

        range.surroundContents(node);
    }

    [Symbol.iterator]() {
        const parent = this.parent;
        const end = this.endIndex;
        let step = this.startIndex;

        return {
            next() {
                if (step >= end) {
                    return { value: null, done: true };
                }

                const result = { value: parent.childNodes[step], done: false };
                step++;

                return result;
            },
        };
    }

    /**
     * @remarks
     * Must be sibling child node ranges.
     */
    mergeWith(after: ChildNodeRange): ChildNodeRange {
        return ChildNodeRange.make(this.parent, this.startIndex, after.endIndex);
    }
}
