// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { findClosest } from "./find-above";
import { MatchType } from "./matcher";
import { surroundNoSplitting } from "./no-splitting";
import { unsurround } from "./unsurround";

registerPackage("anki/surround", {
    findClosest,
    MatchType,
    surroundNoSplitting,
    unsurround,
});

export { findClosest, MatchType, surroundNoSplitting, unsurround };
export type { ElementMatcher, Match, SurroundFormat } from "./matcher";
