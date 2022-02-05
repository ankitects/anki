// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { findClosest } from "./find-above";
import { MatchResult } from "./matcher";
import { surroundNoSplitting } from "./no-splitting";
import { unsurround } from "./unsurround";

registerPackage("anki/surround", {
    findClosest,
    MatchResult,
    surroundNoSplitting,
    unsurround,
});

export { findClosest, MatchResult, surroundNoSplitting, unsurround };
export type { SurroundFormat, ElementClearer, ElementMatcher } from "./matcher";
