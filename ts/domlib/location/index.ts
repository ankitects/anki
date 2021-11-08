// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { registerPackage } from "../../lib/register-package";

import { saveSelection, restoreSelection } from "./document";

registerPackage("anki/location", {
    saveSelection,
    restoreSelection,
});

export { saveSelection, restoreSelection };
export type { SelectionLocation } from "./selection";
