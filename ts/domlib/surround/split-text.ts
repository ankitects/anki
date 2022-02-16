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

interface SplitRange {
    /**
     * Can be used to recreate a range with `range.setStartBefore(start)`
     */
    start: Node;
    /**
     * Can be used to recreate a range with `range.setEndAfter(end)`
     */
    end: Node;
}

export function splitPartiallySelected(range: Range): SplitRange {
    let start: Node;
    if (nodeIsText(range.startContainer)) {
        start = splitTextIfNecessary(range.startContainer, range.startOffset);
    } else {
        start = range.startContainer;
    }

    let end = range.endContainer;
    if (nodeIsText(range.endContainer)) {
        splitTextIfNecessary(range.endContainer, range.endOffset);
    }

    return { start, end };
}
