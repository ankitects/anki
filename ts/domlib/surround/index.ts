// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { findClosest } from "./find-above";
import { MatchResult, matchTagName } from "./matcher";
import { surroundNoSplitting } from "./no-splitting";
import { unsurround } from "./unsurround";

registerPackage("anki/surround", {
    surroundNoSplitting,
    unsurround,
    findClosest,
    MatchResult,
    matchTagName,
});

export { findClosest, MatchResult, matchTagName, surroundNoSplitting, unsurround };
export type { ElementClearer, ElementMatcher } from "./matcher";
