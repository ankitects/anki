// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";

/**
 * @returns Split text node to end direction
 */
function splitText(node: Text, offset: number): Text {
    return node.splitText(offset);
}

interface SplitRange {
    start: Text | null;
    end: Text | null;
}

export function splitPartiallySelectedTextNodes(range: Range): SplitRange {
    const startContainer = range.startContainer;
    const startOffset = range.startOffset;

    const start = nodeIsText(startContainer)
        ? splitText(startContainer, startOffset)
        : null;

    const endContainer = range.endContainer;
    const endOffset = range.endOffset;

    let end: Text | null = null;
    if (nodeIsText(endContainer)) {
        const splitOff = splitText(endContainer, endOffset);

        if (splitOff.data.length === 0) {
            /**
             * Range should include split text if zero-length
             * For the start container, this is done automatically
             */

            end = splitOff;
            range.setEndAfter(end);
        } else {
            end = endContainer;
        }
    }

    return { start, end };
}

/* returned in source order */
export function findTextNodesWithin(node: Node): Text[] {
    if (nodeIsText(node)) {
        return [node];
    } else if (nodeIsElement(node)) {
        return Array.from(node.childNodes).reduce(
            (accumulator: Text[], value) => [
                ...accumulator,
                ...findTextNodesWithin(value),
            ],
            [],
        );
    } else {
        return [];
    }
}
