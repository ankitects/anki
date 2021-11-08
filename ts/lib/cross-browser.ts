// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Firefox has no .getSelection on ShadowRoot, only .activeElement
 */
export function getSelection(element: Node): Selection | null {
    const root = element.getRootNode();

    if (root.getSelection) {
        return root.getSelection();
    }

    return null;
}
