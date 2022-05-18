// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * @returns True, if element has no style attribute (anymore).
 */
function removeEmptyStyle(element: HTMLElement | SVGElement): boolean {
    if (element.style.cssText.length === 0) {
        element.removeAttribute("style");
        // Calling `.hasAttribute` right after `.removeAttribute` might return true.
        return true;
    }

    return false;
}

/**
 * Will remove the style attribute, if all properties were removed.
 *
 * @returns True, if element has no style attributes anymore
 */
export function removeStyleProperties(
    element: HTMLElement | SVGElement,
    ...props: string[]
): boolean {
    for (const prop of props) {
        element.style.removeProperty(prop);
    }

    return removeEmptyStyle(element);
}
