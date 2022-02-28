// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "../lib/events";

/**
 * Typically the right-sided mouse button.
 */
function isSecondaryButton(event: MouseEvent): boolean {
    return event.button === 2;
}

function closeOnClick(popover: HTMLElement): { destroy(): void } {
    function shouldClose(event: MouseEvent): boolean {
        if (isSecondaryButton(event)) {
            return false;
        }

        const path = event.composedPath();

        if (path.includes(popover)) {
            return false;
        }

        return true;
    }

    function popupShouldClose(this: Document, event: MouseEvent): void {
        popover.hidden = shouldClose(event);
    }

    const destroy = on(document, "click", popupShouldClose);

    return {
        destroy,
    };
}

export default closeOnClick;
