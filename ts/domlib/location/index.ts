// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { restoreSelection, saveSelection } from "./document";
import { Position } from "./location";

registerPackage("anki/location", {
    saveSelection,
    restoreSelection,
    Position,
});

export { Position, restoreSelection, saveSelection };
export type { SelectionLocation } from "./selection";
