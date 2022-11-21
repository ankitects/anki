// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { PlainTextInputAPI } from "./plain-text-input";
import type { RichTextInputAPI } from "./rich-text-input";

function isFontElement(element: Element): element is HTMLFontElement {
    return element.tagName === "FONT";
}

/**
 * Avoid both HTMLFontElement and .color, as they are both deprecated
 */
export function withFontColor(
    element: Element,
    callback: (color: string) => void,
): boolean {
    if (isFontElement(element)) {
        callback(element.color);
        return true;
    }

    return false;
}

/***
 * Required for field inputs wrapped in Collapsible
 */
export async function refocusInput(
    api: RichTextInputAPI | PlainTextInputAPI,
): Promise<void> {
    do {
        await new Promise(window.requestAnimationFrame);
    } while (!api.focusable);
    api.refocus();
}
