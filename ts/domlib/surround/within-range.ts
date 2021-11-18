// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { Position } from "../location";

export function nodeWithinRange(node: Node, range: Range): boolean {
    const nodeRange = new Range();
    /* range.startContainer and range.endContainer will be Text */
    nodeRange.selectNodeContents(node);

    return (
        range.compareBoundaryPoints(Range.START_TO_START, nodeRange) !==
            Position.After &&
        range.compareBoundaryPoints(Range.END_TO_END, nodeRange) !== Position.Before
    );
}
