// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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

    if (keepFocus) {
        saveFieldTimer.fireImmediately();
    } else {
        // triggers onBlur, which saves
        saveFieldTimer.clear();
        currentField.blur();
    }
}
