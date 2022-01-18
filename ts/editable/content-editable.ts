// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on, preventDefault } from "../lib/events";
import { registerShortcut } from "../lib/shortcuts";
import { placeCaretAfterContent } from "../domlib/place-caret";
import { saveSelection, restoreSelection } from "../domlib/location";
import { isApplePlatform } from "../lib/platform";
import { bridgeCommand } from "../lib/bridgecommand";
import type { SelectionLocation } from "../domlib/location";

function safePlaceCaretAfterContent(editable: HTMLElement): void {
    /**
     * Workaround: If you try to invoke an IME after calling
     * `placeCaretAfterContent` on a cE element, the IME will immediately
     * end and the input character will be duplicated
     */
    placeCaretAfterContent(editable);
    restoreSelection(editable, saveSelection(editable)!);
}

function onFocus(location: SelectionLocation | null): () => void {
    return function (this: HTMLElement): void {
        if (!location) {
            safePlaceCaretAfterContent(this);
            return;
        }

        try {
            restoreSelection(this, location);
        } catch {
            safePlaceCaretAfterContent(this);
        }
    };
}

export function customFocusHandling() {
    const focusHandlingEvents: (() => void)[] = [];

    function flushEvents(): void {
        let removeEvent: (() => void) | undefined;

        while ((removeEvent = focusHandlingEvents.pop())) {
            removeEvent();
        }
    }

    function prepareFocusHandling(
        editable: HTMLElement,
        latestLocation: SelectionLocation | null = null,
    ): void {
        const off = on(editable, "focus", onFocus(latestLocation), { once: true });

        focusHandlingEvents.push(off, on(editable, "pointerdown", off, { once: true }));
    }

    /**
     * Must execute before DOMMirror.
     */
    function onBlur(this: HTMLElement): void {
        prepareFocusHandling(this, saveSelection(this));
    }

    function setupFocusHandling(editable: HTMLElement): { destroy(): void } {
        prepareFocusHandling(editable);
        const off = on(editable, "blur", onBlur);

        return {
            destroy() {
                flushEvents();
                off();
            },
        };
    }

    return {
        setupFocusHandling,
        flushCaret: flushEvents,
    };
}

if (isApplePlatform()) {
    registerShortcut(() => bridgeCommand("paste"), "Control+Shift+V");
}

export function preventBuiltinContentEditableShortcuts(editable: HTMLElement): void {
    for (const keyCombination of ["Control+B", "Control+U", "Control+I", "Control+R"]) {
        registerShortcut(preventDefault, keyCombination, editable);
    }
}

/** API */

export interface ContentEditableAPI {
    flushCaret(): void;
}
