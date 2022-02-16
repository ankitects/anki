// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EvaluateFormat } from "../evaluate-format";
import { FormattingNode } from "../formatting-tree";
import type { ParseFormat } from "../parse-format";
import { buildFromNode } from "./build";
import { extendAndMerge } from "./extend-merge";

export function build(
    node: Node,
    format: ParseFormat,
    evaluateFormat: EvaluateFormat,
    covered: boolean,
): void {
    let output = buildFromNode(node, format, covered);

    if (output instanceof FormattingNode) {
        output = extendAndMerge(output, format);
    }

    console.log("formatting tree", output);
    debugger;

    output?.evaluate(evaluateFormat, 0);
}
