import type { EditingArea } from ".";

import { getCurrentField } from ".";
import { bridgeCommand } from "./lib";
import { getNoteId } from "./noteId";
import { updateButtonState } from "./toolbar";

let changeTimer: number | null = null;

export function triggerChangeTimer(currentField: EditingArea): void {
    clearChangeTimer();
    changeTimer = setTimeout(function () {
        updateButtonState();
        saveField(currentField, "key");
    }, 600);
}

function clearChangeTimer(): void {
    if (changeTimer) {
        clearTimeout(changeTimer);
        changeTimer = null;
    }
}

export function saveField(currentField: EditingArea, type: "blur" | "key"): void {
    clearChangeTimer();
    bridgeCommand(
        `${type}:${currentField.ord}:${getNoteId()}:${currentField.fieldHTML}`
    );
}

export function saveNow(keepFocus: boolean): void {
    const currentField = getCurrentField();

    if (!currentField) {
        return;
    }

    clearChangeTimer();

    if (keepFocus) {
        saveField(currentField, "key");
    } else {
        // triggers onBlur, which saves
        currentField.blurEditable();
    }
}
