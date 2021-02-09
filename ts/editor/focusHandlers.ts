import type { EditingArea, EditorField } from ".";

import { bridgeCommand } from "./lib";
import { enableButtons, disableButtons } from "./toolbar";
import { saveField } from "./changeTimer";

enum ViewportRelativePosition {
    Contained,
    ExceedTop,
    ExceedBottom,
}

function isFieldInViewport(
    element: Element,
    toolbarHeight: number
): ViewportRelativePosition {
    const rect = element.getBoundingClientRect();

    return rect.top <= toolbarHeight
        ? ViewportRelativePosition.ExceedTop
        : rect.bottom >= (window.innerHeight || document.documentElement.clientHeight)
        ? ViewportRelativePosition.ExceedBottom
        : ViewportRelativePosition.Contained;
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

    const editorField = currentField.parentElement! as EditorField;
    const toolbarHeight = document.getElementById("topbutsOuter")!.clientHeight;
    switch (isFieldInViewport(editorField, toolbarHeight)) {
        case ViewportRelativePosition.ExceedBottom:
            editorField.scrollIntoView(false);
            break;
        case ViewportRelativePosition.ExceedTop:
            editorField.scrollIntoView(true);
            window.scrollBy(0, -toolbarHeight);
            break;
    }

    currentField.focusEditable();
    bridgeCommand(`focus:${currentField.ord}`);
    enableButtons();
    // do this twice so that there's no flicker on newer versions
    caretToEnd(currentField);
}

export function onBlur(evt: FocusEvent): void {
    const currentField = evt.currentTarget as EditingArea;

    if (currentField === document.activeElement) {
        // other widget or window focused; current field unchanged
        saveField(currentField, "key");
        previousActiveElement = currentField;
    } else {
        saveField(currentField, "blur");
        disableButtons();
        previousActiveElement = null;
    }
}
