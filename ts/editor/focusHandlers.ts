/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import type { EditingArea } from ".";

import { bridgeCommand } from "./lib";
import { enableButtons, disableButtons } from "./toolbar";
import { saveField } from "./changeTimer";

export function onFocus(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;
    currentField.focusEditable();
    bridgeCommand(`focus:${currentField.ord}`);
    enableButtons();
}

export function onBlur(evt: FocusEvent): void {
    const previousFocus = evt.currentTarget as EditingArea;
    const currentFieldUnchanged = previousFocus === document.activeElement;

    saveField(previousFocus, currentFieldUnchanged ? "key" : "blur");
    disableButtons();
}
