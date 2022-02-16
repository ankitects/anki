// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsText } from "../../lib/dom";

/**
 * @returns Split text node to end direction or text itself if a split is
 * not necessary
 */
function splitTextIfNecessary(text: Text, offset: number): Text {
    if (offset === 0 || offset === text.length) {
        return text;
    }

    return text.splitText(offset);
}

/**
 * @link https://dom.spec.whatwg.org/#concept-node-length
 */
function length(node: Node): number {
    if (node instanceof CharacterData) {
        return node.length;
    } else if (
        node.nodeType === Node.DOCUMENT_TYPE_NODE ||
        node.nodeType === Node.ATTRIBUTE_NODE
    ) {
        return 0;
    }

    return node.childNodes.length;
}

export class SplitRange {
    constructor(protected start: Node, protected end: Node) {}

    private adjustStart(): void {
        if (this.start.firstChild) {
            this.start = this.start.firstChild;
        } else if (this.start.nextSibling) {
            this.start = this.start.nextSibling!;
        }
    }

    private adjustEnd(): void {
        if (this.end.lastChild) {
            this.end = this.end.lastChild!;
        } else if (this.end.previousSibling) {
            this.end = this.end.previousSibling;
        }
    }

    adjustRange(element: Element): void {
        if (this.start === element) {
            this.adjustStart();
        } else if (this.end === element) {
            this.adjustEnd();
        }
    }

    /**
     * Returns a range with boundary points `(start, 0)` and `(end, end.length)`
     */
    recreateRange(): Range {
        const range = new Range();
        range.setStart(this.start, 0);
        range.setEnd(this.end, length(this.end));
        return range;
    }
}

export function splitPartiallySelected(range: Range): SplitRange {
    let start: Node;
    if (nodeIsText(range.startContainer)) {
        start = splitTextIfNecessary(range.startContainer, range.startOffset);
    } else {
        start = range.startContainer;
    }

    const end = range.endContainer;
    if (nodeIsText(range.endContainer)) {
        splitTextIfNecessary(range.endContainer, range.endOffset);
    }

    return new SplitRange(start, end);
}
