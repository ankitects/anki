// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { MatchType } from "./match-type";
import { reformat, surround, unsurround } from "./no-splitting";

registerPackage("anki/surround", {
    MatchType,
});

export { MatchType, reformat, surround, unsurround };
export type { ElementMatcher, Match, SurroundFormat } from "./match-type";
