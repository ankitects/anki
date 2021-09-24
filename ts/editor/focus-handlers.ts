// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

// import { fieldFocused } from "./toolbar";
// import type { EditingArea } from "./editing-area";

// import { saveField } from "./saving";
// import { bridgeCommand } from "./lib";
// import { getCurrentField } from "./helpers";

// export function deferFocusDown(editingArea: EditingArea): void {
//     editingArea.focus();
//     editingArea.caretToEnd();

//     if (editingArea.getSelection().anchorNode === null) {
//         // selection is not inside editable after focusing
//         editingArea.caretToEnd();
//     }

//     bridgeCommand(`focus:${editingArea.ord}`);
//     // fieldFocused.set(true);
// }

// export function saveFieldIfFieldChanged(
//     editingArea: EditingArea,
//     focusTo: Element | null
// ): void {
//     const fieldChanged =
//         editingArea !== getCurrentField() && !editingArea.contains(focusTo);

//     saveField(editingArea, fieldChanged ? "blur" : "key");
//     // fieldFocused.set(false);

//     if (fieldChanged) {
//         editingArea.resetHandles();
//     }
// }
