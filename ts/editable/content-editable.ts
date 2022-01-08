// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on, preventDefault } from "../lib/events";
import { registerShortcut } from "../lib/shortcuts";
import { placeCaretAfterContent } from "../domlib/place-caret";
import { saveSelection, restoreSelection } from "../domlib/location";
import type { SelectionLocation } from "../domlib/location";

const locationEvents: (() => void)[] = [];

function flushLocation(): void {
    let removeEvent: (() => void) | undefined;

    while ((removeEvent = locationEvents.pop())) {
        removeEvent();
    }
}

let latestLocation: SelectionLocation | null = null;

function onFocus(this: HTMLElement): void {
    if (!latestLocation) {
        placeCaretAfterContent(this);
        return;
    }

    try {
        restoreSelection(this, latestLocation);
    } catch {
        placeCaretAfterContent(this);
    }
}

function onBlur(this: HTMLElement): void {
    prepareFocusHandling(this);
    latestLocation = saveSelection(this);
}

let removeOnFocus: () => void;

export function prepareFocusHandling(editable: HTMLElement): void {
    removeOnFocus = on(editable, "focus", onFocus, { once: true });

    locationEvents.push(
        removeOnFocus,
        on(editable, "pointerdown", removeOnFocus, { once: true }),
    );
}

/* Must execute before DOMMirror */
export function saveLocation(editable: HTMLElement): { destroy(): void } {
    const removeOnBlur = on(editable, "blur", onBlur);

    return {
        destroy() {
            removeOnBlur();
            flushLocation();
        },
    };
}

export function preventBuiltinContentEditableShortcuts(editable: HTMLElement): void {
    for (const keyCombination of ["Control+B", "Control+U", "Control+I", "Control+R"]) {
        registerShortcut(preventDefault, keyCombination, editable);
    }
}

/** API */

export interface ContentEditableAPI {
    flushLocation(): void;
}

const contentEditableApi: ContentEditableAPI = {
    flushLocation,
};

export default contentEditableApi;
