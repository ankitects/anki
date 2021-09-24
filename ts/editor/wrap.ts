// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { wrapInternal } from "lib/wrap";
// import { getCurrentField } from ".";

export function wrap(front: string, back: string): void {
    // const editingArea = getCurrentField();
    // if (editingArea) {
    // wrapInternal(editingArea.editableContainer.shadowRoot!, front, back, false);
    // }
}

export function wrapCurrent(front: string, back: string): void {
    // const currentField = getCurrentField()!;
    // currentField.surroundSelection(front, back);
}

/* currently unused */
export function wrapIntoText(front: string, back: string): void {
    // const editingArea = getCurrentField();
    // if (editingArea) {
    // wrapInternal(editingArea.editableContainer.shadowRoot!, front, back, false);
    // }
}
