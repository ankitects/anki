// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement } from "../../lib/dom";
import { ascend } from "../../lib/node";

export class ChildNodeRange {
    private constructor(
        public parent: Node,
        public startIndex: number = 0,
        public endIndex: number = 0,
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
}
