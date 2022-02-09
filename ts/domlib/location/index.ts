// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/runtime-require";
import { restoreSelection, saveSelection } from "./document";
import { Position } from "./location";
import { getNodeCoordinates, findNodeFromCoordinates } from "./node";

registerPackage("anki/location", {
    Position,
    restoreSelection,
    saveSelection,
});

export { Position, restoreSelection, saveSelection, getNodeCoordinates, findNodeFromCoordinates };
export type { SelectionLocation } from "./selection";
