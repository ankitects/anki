// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * NOTES:
 * - Avoid using selection.isCollapsed: will always return true in shadow root in Gecko
 */

/**
 * Gecko has no .getSelection on ShadowRoot, only .activeElement
 */
export function getSelection(element: Node): Selection | null {
    const root = element.getRootNode();

    if (root.getSelection) {
        return root.getSelection();
    }

    return document.getSelection();
}

/**
 * Browser has potential support for multiple ranges per selection built in,
 * but in reality only Gecko supports it.
 * If there are multiple ranges, the latest range is the _main_ one.
 */
export function getRange(selection: Selection): Range | null {
    const rangeCount = selection.rangeCount;

    return rangeCount === 0 ? null : selection.getRangeAt(rangeCount - 1);
}
