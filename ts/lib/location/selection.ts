// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getNodeCoordinates } from "./node";
import type { CaretLocation } from "./location";
import { compareLocations, Position } from "./location";

interface SelectionLocationCollapsed {
    readonly anchor: CaretLocation;
    readonly collapsed: true;
}

interface SelectionLocationContent {
    readonly anchor: CaretLocation;
    readonly focus: CaretLocation;
    readonly collapsed: false;
    readonly direction: "forward" | "backward";
}

export type SelectionLocation = SelectionLocationCollapsed | SelectionLocationContent;

/* Gecko can have multiple ranges in the selection
/* this function will get the coordinates of the latest one created */
export function getSelectionLocation(selection: Selection): SelectionLocation | null {
    const range = selection.getRangeAt(selection.rangeCount - 1);
    const root = range.commonAncestorContainer.getRootNode();

    if (selection.rangeCount === 0) {
        return null;
    }

    const anchorCoordinates = getNodeCoordinates(selection.anchorNode!, root);
    const anchor = { coordinates: anchorCoordinates, offset: selection.anchorOffset };

    /* selection.isCollapsed will always return true in shadow root in Gecko */
    const collapsed = range.collapsed;

    if (collapsed) {
        return { anchor, collapsed };
    }

    const focusCoordinates = getNodeCoordinates(selection.focusNode!, root);
    const focus = { coordinates: focusCoordinates, offset: selection.focusOffset };
    const position = compareLocations(anchor, focus);

    const direction = position === Position.After ? "backward" : "forward";

    return {
        anchor,
        focus,
        collapsed,
        direction,
    };
}
