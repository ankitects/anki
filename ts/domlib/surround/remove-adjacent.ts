// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ChildNodeRange } from "./child-node-range";
import { findAfter, findBefore } from "./find-adjacent";
import type { ElementMatcher } from "./matcher";

export function extendBefore(range: ChildNodeRange, matcher: ElementMatcher): void {
    const matches = findBefore(range, matcher);
    range.startIndex -= matches.length;
}

export function extendAfter(range: ChildNodeRange, matcher: ElementMatcher): void {
    const matches = findAfter(range, matcher);
    range.endIndex += matches.length;
}
