// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EvaluateFormat } from "../format-evaluate";
import { FormattingNode } from "../formatting-tree";
import type { ParseFormat } from "../format-parse";
import { buildFromNode } from "./build";
import { extendAndMerge } from "./extend-merge";

export function build(
    node: Node,
    format: ParseFormat,
    evaluateFormat: EvaluateFormat,
    insideMatch: boolean,
): void {
    let output = buildFromNode(node, format, insideMatch);

    if (output instanceof FormattingNode) {
        output = extendAndMerge(output, format);
    }

    output?.evaluate(evaluateFormat, 0);
}
