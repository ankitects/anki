/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

import { EditingArea } from ".";

import { bridgeCommand } from "./lib";
import { enableButtons, disableButtons } from "./toolbar";
import { saveField } from "./changeTimer";

function caretToEnd(currentField: EditingArea): void {
    const range = document.createRange();
    range.selectNodeContents(currentField.editable);
    range.collapse(false);
    const selection = currentField.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
}

function focusField(field: EditingArea, moveCaretToEnd: boolean): void {
    field.focusEditable();
    bridgeCommand(`focus:${field.ord}`);

    if (moveCaretToEnd) {
        caretToEnd(field);
    }

    enableButtons();
}

// For distinguishing focus by refocusing window from deliberate focus
let previousActiveElement: EditingArea | null = null;

export function onFocus(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;
    const previousFocus = evt.relatedTarget as EditingArea;

    if (
        !(previousFocus instanceof EditingArea) ||
        previousFocus === previousActiveElement
    ) {
        const moveCaretToEnd =
            Boolean(previousFocus) || !Boolean(previousActiveElement);

        focusField(currentField, moveCaretToEnd);
    }
}

export function onBlur(evt: FocusEvent): void {
    const previousFocus = evt.currentTarget as EditingArea;

    saveField(previousFocus, previousFocus === document.activeElement ? "key" : "blur");
    // other widget or window focused; current field unchanged
    previousActiveElement = previousFocus;
    disableButtons();
}
