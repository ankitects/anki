// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/register-package";

import { saveSelection, restoreSelection } from "./document";
import { Position } from "./location";

registerPackage("anki/location", {
    saveSelection,
    restoreSelection,
    Position,
});

export { saveSelection, restoreSelection, Position };
export type { SelectionLocation } from "./selection";
