// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "@tslib/events";

interface ModalClosingHandler {
    set: (value: boolean) => void;
    remove: () => void;
}

/**
 * Register a keydown handler on the document that can optionally stop propagation to other handlers if Escape is pressed and the associated flag is set.
 * Intended to override the general handler in webview.py when a modal is open.
 */
function registerModalClosingHandler(callback?: () => void): ModalClosingHandler {
    let modalIsOpen = false;

    function set(value: boolean) {
        modalIsOpen = value;
    }

    const remove = on(document, "keydown", (event) => {
        if (event.key === "Escape" && modalIsOpen) {
            event.stopImmediatePropagation();
            if (callback) {
                callback();
            }
        }
    }, { capture: true });

    return { set, remove };
}

export { registerModalClosingHandler };
