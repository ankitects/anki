// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { ascend, isOnlyChild } from "../node";
import { elementIsBlock } from "../dom";

export function ascendWhileSingleInline(node: Node, base: Node): Node {
    if (node.isSameNode(base)) {
        return node;
    }

    while (
        isOnlyChild(node) &&
        node.parentElement &&
        !elementIsBlock(node.parentElement) &&
        node.parentElement !== base
    ) {
        node = ascend(node);
    }

    return node;
}
