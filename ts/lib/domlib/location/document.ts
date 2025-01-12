// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection } from "@tslib/cross-browser";

import { findNodeFromCoordinates } from "./node";
import type { SelectionLocation, SelectionLocationContent } from "./selection";
import { getSelectionLocation } from "./selection";

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

export function saveSelection(base: Node): SelectionLocation | null {
    return getSelectionLocation(base);
}

export function restoreSelection(base: Node, location: SelectionLocation): void {
    const selection = getSelection(base)!;
    unselect(selection);

    const range = new Range();
    const anchorNode = findNodeFromCoordinates(base, location.anchor.coordinates);
    range.setStart(anchorNode!, location.anchor.offset!);

    if (location.collapsed) {
        range.collapse(true);
        selection.addRange(range);
    } else {
        setSelectionToLocationContent(
            base,
            selection,
            range,
            location,
        );
    }
}
