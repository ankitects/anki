// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { elementIsBlock } from "../../lib/dom";
import { ascend, isOnlyChild } from "../../lib/node";

export function ascendWhileSingleInline(node: Node, base: Node): Node {
    if (node === base) {
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
