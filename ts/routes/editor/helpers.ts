// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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

export class Flag {
    private flag: boolean;

    constructor() {
        this.flag = false;
    }

    setFlag(on: boolean): void {
        this.flag = on;
    }

    /** Resets the flag to false and returns the previous value. */
    checkAndReset(): boolean {
        const val = this.flag;
        this.flag = false;
        return val;
    }
}
