// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";

import { surroundNoSplitting } from "./no-splitting";
import { unsurround } from "./unsurround";
import { findClosest } from "./find-above";
import { MatchResult, matchTagName } from "./matcher";

registerPackage("anki/surround", {
    surroundNoSplitting,
    unsurround,
    findClosest,
    MatchResult,
    matchTagName,
});

export { surroundNoSplitting, unsurround, findClosest, MatchResult, matchTagName };
export type { ElementMatcher, ElementClearer } from "./matcher";
