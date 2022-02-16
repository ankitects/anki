// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { UnsurroundEvaluateFormat } from "./evaluate-format";
import type { SurroundFormat } from "./match-type";
import { reformatRange } from "./no-splitting";
import { UnsurroundParseFormat } from "./parse-format";

/**
 * The counterpart to `surroundNoSplitting`.
 *
 * @param range: The range to unsurround
 * @param base: Surrounding will not ascend beyond this point. `base.contains(range.commonAncestorContainer)` should be true.
 **/
export function unsurround(range: Range, base: Element, format: SurroundFormat): Range {
    return reformatRange(
        range,
        UnsurroundParseFormat.make(format, base, range),
        UnsurroundEvaluateFormat.make(format),
    );
}
