// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EvaluateFormat } from "../format-evaluate";
import type { ParseFormat } from "../format-parse";
import { buildFromNode } from "./build";
import { extendAndMerge } from "./extend-merge";

export function build(
    /**
     * There should be no matching elements above node.
     */
    node: Node,
    parse: ParseFormat,
    evaluate: EvaluateFormat,
): Range {
    const trees = extendAndMerge(buildFromNode(node, parse, []), parse);

    for (const tree of trees) {
        tree.evaluate(evaluate, 0);
    }

    return parse.recreateRange();
}
