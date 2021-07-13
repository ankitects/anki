// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

import { fieldFocused } from "./toolbar";
import type { EditingArea } from "./editing-area";

import { saveField } from "./change-timer";
import { bridgeCommand } from "./lib";

export function onFocus(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;
    currentField.focus();

    if (currentField.shadowRoot!.getSelection()!.anchorNode === null) {
        // selection is not inside editable after focusing
        currentField.caretToEnd();
    }

    bridgeCommand(`focus:${currentField.ord}`);
    fieldFocused.set(true);
}

export function onBlur(evt: FocusEvent): void {
    const previousFocus = evt.currentTarget as EditingArea;
    const currentFieldUnchanged = previousFocus === document.activeElement;

    saveField(previousFocus, currentFieldUnchanged ? "key" : "blur");
    fieldFocused.set(false);
}
