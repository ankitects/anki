// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SelectionLocation } from "../domlib/location";
import { restoreSelection, saveSelection } from "../domlib/location";
import { placeCaretAfterContent } from "../domlib/place-caret";
import { bridgeCommand } from "../lib/bridgecommand";
import { on, preventDefault } from "../lib/events";
import { isApplePlatform } from "../lib/platform";
import { registerShortcut } from "../lib/shortcuts";
import type { Callback } from "../lib/typing";
import { HandlerList } from "../sveltelib/handler-list";

/**
 * Workaround: If you try to invoke an IME after calling
 * `placeCaretAfterContent` on a cE element, the IME will immediately
 * end and the input character will be duplicated
 */
function safePlaceCaretAfterContent(editable: HTMLElement): void {
    placeCaretAfterContent(editable);
    restoreSelection(editable, saveSelection(editable)!);
}

function restoreCaret(element: HTMLElement, location: SelectionLocation | null): void {
    if (!location) {
        return safePlaceCaretAfterContent(element);
    }

    try {
        restoreSelection(element, location);
    } catch {
        safePlaceCaretAfterContent(element);
    }
}

type SetupFocusHandlerAction = (element: HTMLElement) => { destroy(): void };

export interface FocusHandlerAPI {
    /**
     * Prevent the automatic caret restoration, that happens upon field focus
     */
    flushCaret(): void;
    /**
     * Executed upon focus event of editable.
     */
    focus: HandlerList<{ event: FocusEvent }>;
}

export function useFocusHandler(): [FocusHandlerAPI, SetupFocusHandlerAction] {
    let latestLocation: SelectionLocation | null = null;
    let offFocus: Callback | null;
    let offPointerDown: Callback | null;
    let flush = false;

    function flushCaret(): void {
        flush = true;
    }

    const focus = new HandlerList<{ event: FocusEvent }>();

    function prepareFocusHandling(
        editable: HTMLElement,
        location: SelectionLocation | null = null,
    ): void {
        latestLocation = location;

        offFocus?.();
        offFocus = on(
            editable,
            "focus",
            (event: FocusEvent): void => {
                if (flush) {
                    flush = false;
                } else {
                    restoreCaret(event.currentTarget as HTMLElement, latestLocation);
                }

                focus.dispatch({ event });
            },
            { once: true },
        );
        offPointerDown?.();
        offPointerDown = on(
            editable,
            "pointerdown",
            () => {
                offFocus?.();
                offFocus = null;
            },
            { once: true },
        );
    }

    /**
     * Must execute before DOMMirror.
     */
    function onBlur(this: HTMLElement): void {
        prepareFocusHandling(this, saveSelection(this));
    }

    function setupFocusHandler(editable: HTMLElement): { destroy(): void } {
        prepareFocusHandling(editable);
        const off = on(editable, "blur", onBlur);

        return {
            destroy() {
                off();
                offFocus?.();
                offPointerDown?.();
            },
        };
    }

    return [
        {
            flushCaret,
            focus,
        },
        setupFocusHandler,
    ];
}

if (isApplePlatform()) {
    registerShortcut(() => bridgeCommand("paste"), "Control+Shift+V");
}

export function preventBuiltinShortcuts(editable: HTMLElement): void {
    for (const keyCombination of ["Control+B", "Control+U", "Control+I"]) {
        registerShortcut(preventDefault, keyCombination, editable);
    }
}

/** API */

export interface ContentEditableAPI {
    /**
     * Can be used to turn off the caret restoring functionality of
     * the ContentEditable. Can be used when you want to set the caret
     * yourself.
     */
    focusHandler: FocusHandlerAPI;
}
