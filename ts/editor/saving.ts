import type { EditingArea } from "./editing-area";

import { ChangeTimer } from "./change-timer";
import { getCurrentField } from "./helpers";
import { bridgeCommand } from "./lib";
import { getNoteId } from "./note-id";

const saveFieldTimer = new ChangeTimer();

export function triggerChangeTimer(currentField: EditingArea): void {
    saveFieldTimer.schedule(() => saveField(currentField, "key"), 600);
}

export function saveField(currentField: EditingArea, type: "blur" | "key"): void {
    saveFieldTimer.clear();
    const command = `${type}:${currentField.ord}:${getNoteId()}:${
        currentField.fieldHTML
    }`;
    bridgeCommand(command);
}

export function saveNow(keepFocus: boolean): void {
    const currentField = getCurrentField();

    if (!currentField) {
        return;
    }

    saveFieldTimer.clear();

    if (keepFocus) {
        saveField(currentField, "key");
    } else {
        // triggers onBlur, which saves
        currentField.blur();
    }
}
