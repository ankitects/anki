// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SelectionLocation } from "./selection";
import { getSelectionLocation } from "./selection";
import { findNodeFromCoordinates } from "./node";

export function saveSelection(root: Document | ShadowRoot): SelectionLocation | null {
    return getSelectionLocation(root.getSelection()!);
}

function unselect(selection: Selection): void {
    selection.empty();
}

export function restoreSelection(
    root: Document | ShadowRoot,
    location: SelectionLocation,
): void {
    const selection = root.getSelection()!;

    unselect(selection);

    if (location.anchor.coordinates.length === 0) {
        /* nothing was selected */
        return;
    }

    const range = new Range();

    const anchorLocation = location.anchor;
    const anchorOffset = anchorLocation.offset;
    const anchorNode = findNodeFromCoordinates(anchorLocation.coordinates, root);
    range.setStart(anchorNode!, anchorOffset!);

    if (location.collapsed) {
        range.collapse(true);
        selection.addRange(range);
        return;
    }

    const focusLocation = location.focus;
    const focusOffset = focusLocation.offset;
    const focusNode = findNodeFromCoordinates(focusLocation.coordinates, root);

    if (location.direction === "forward") {
        range.setEnd(focusNode!, focusOffset!);
        selection.addRange(range);
    } /* location.direction === "backward" */ else {
        selection.addRange(range);
        selection.extend(focusNode!, focusOffset!);
    }
}
