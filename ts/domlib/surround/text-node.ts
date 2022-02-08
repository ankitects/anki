// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "../../lib/dom";
import { nodeWithinRange } from "./within-range";

/**
 * @returns Split text node to end direction
 */
function splitText(node: Text, offset: number): Text {
    return node.splitText(offset);
}

// TODO maybe both start and end should be of type Node
// Could also probably be the new "Range anchors"
interface SplitRange {
    /**
     * Used to recreate a range: `range.setStartBefore(start)`
     */
    start: Text | null;
    /**
     * Used to recreate a range: `range.setEndAfter(end)`
     */
    end: Text | null;
}

export function splitPartiallySelectedTextNodes(range: Range): SplitRange {
    const startContainer = range.startContainer;
    const startOffset = range.startOffset;

    // TODO Maybe we should avoid splitting, if they
    // create zero-length text nodes

    const start = nodeIsText(startContainer)
        ? splitText(startContainer, startOffset)
        : null;

    const endContainer = range.endContainer;
    const endOffset = range.endOffset;

    let end: Text | null = null;
    if (nodeIsText(endContainer)) {
        const splitOff = splitText(endContainer, endOffset);

        if (splitOff.data.length === 0) {
            // Range should include the split-off text if it is zero-length.
            // For the start container, this is done automatically.
            end = splitOff;
            range.setEndAfter(end);
        } else {
            end = endContainer;
        }
    }

    return { start, end };
}

/**
 * @returns Text nodes contained in node in source order
 */
function findTextsWithinNode(node: Node): Text[] {
    if (nodeIsText(node)) {
        return [node];
    } else if (nodeIsElement(node)) {
        const nodes: Text[] = [];

        for (const child of node.childNodes) {
            nodes.push(...findTextsWithinNode(child));
        }

        return nodes;
    } else {
        return [];
    }
}

/**
 * @returns Text nodes contained in range in source order
 */
export function findTextsWithinRange(range: Range): Text[] {
    return findTextsWithinNode(range.commonAncestorContainer).filter(
        (text: Text): boolean => nodeWithinRange(text, range),
    );
}

/**
 * We do not want to surround zero-length text nodes
 */
export function validText(text: Text): boolean {
    return text.length > 0;
}
