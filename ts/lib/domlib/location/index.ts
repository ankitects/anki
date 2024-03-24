// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "@tslib/runtime-require";

import { restoreSelection, saveSelection } from "./document";
import { Position } from "./location";
import { findNodeFromCoordinates, getNodeCoordinates } from "./node";
import { getRangeCoordinates } from "./range";

registerPackage("anki/location", {
    Position,
    restoreSelection,
    saveSelection,
});

export { findNodeFromCoordinates, getNodeCoordinates, getRangeCoordinates, Position, restoreSelection, saveSelection };
export type { RangeCoordinates } from "./range";
export type { SelectionLocation } from "./selection";
