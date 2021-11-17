import { registerPackage } from "../../lib/register-package";

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
