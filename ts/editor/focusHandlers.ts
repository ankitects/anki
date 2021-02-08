import type { EditingArea } from ".";

import { bridgeCommand } from "./lib";
import { enableButtons, disableButtons } from "./toolbar";
import { saveField } from "./changeTimer";

function isElementInViewport(element: Element): boolean {
    const rect = element.getBoundingClientRect();

    return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
        rect.right <= (window.innerWidth || document.documentElement.clientWidth)
    );
}

function caretToEnd(currentField: EditingArea): void {
    const range = document.createRange();
    range.selectNodeContents(currentField.editable);
    range.collapse(false);
    const selection = currentField.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
}

// For distinguishing focus by refocusing window from deliberate focus
let previousActiveElement: EditingArea | null = null;

export function onFocus(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;

    if (currentField === previousActiveElement) {
        return;
    }

    currentField.focusEditable();
    bridgeCommand(`focus:${currentField.ord}`);
    enableButtons();
    // do this twice so that there's no flicker on newer versions
    caretToEnd(currentField);
    // scroll if bottom of element off the screen
    if (!isElementInViewport(currentField)) {
        currentField.scrollIntoView(false /* alignToBottom */);
    }
}

export function onBlur(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;

    if (currentField === previousActiveElement) {
        // other widget or window focused; current field unchanged
        saveField(currentField, "key");
        previousActiveElement = currentField;
    } else {
        saveField(currentField, "blur");
        disableButtons();
        previousActiveElement = null;
    }
}
