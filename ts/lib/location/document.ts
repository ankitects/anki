// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SelectionLocation, SelectionLocationContent } from "./selection";
import { getSelectionLocation } from "./selection";
import { findNodeFromCoordinates } from "./node";
import { getSelection } from "../cross-browser";

export function saveSelection(root: Node): SelectionLocation | null {
    return getSelectionLocation(getSelection(root)!);
}

function unselect(selection: Selection): void {
    selection.empty();
}

function setSelectionToLocationContent(
    node: Node,
    selection: Selection,
    range: Range,
    location: SelectionLocationContent,
) {
    const focusLocation = location.focus;
    const focusOffset = focusLocation.offset;
    const focusNode = findNodeFromCoordinates(node, focusLocation.coordinates);

    if (location.direction === "forward") {
        range.setEnd(focusNode!, focusOffset!);
        selection.addRange(range);
    } /* location.direction === "backward" */ else {
        selection.addRange(range);
        selection.extend(focusNode!, focusOffset!);
    }
}

export function restoreSelection(node: Node, location: SelectionLocation): void {
    const selection = getSelection(node)!;

    unselect(selection);

    if (location.anchor.coordinates.length === 0) {
        /* nothing was selected */
        return;
    }

    const range = new Range();

    const anchorLocation = location.anchor;
    const anchorOffset = anchorLocation.offset;
    const anchorNode = findNodeFromCoordinates(node, anchorLocation.coordinates);
    range.setStart(anchorNode!, anchorOffset!);

    if (location.collapsed) {
        range.collapse(true);
        selection.addRange(range);
    } else {
        setSelectionToLocationContent(
            node,
            selection,
            range,
            location as SelectionLocationContent,
        );
    }
}
