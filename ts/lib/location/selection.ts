// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getNodeCoordinates } from "./node";
import type { CaretLocation } from "./location";
import { compareLocations, Order } from "./location";

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
    if (selection.rangeCount === 0) {
        return null;
    }

    const anchorCoordinates = getNodeCoordinates(selection.anchorNode!);
    const anchor = { coordinates: anchorCoordinates, offset: selection.anchorOffset };
    /* selection.isCollapsed will always return true in shadow root in Gecko */
    const collapsed = selection.getRangeAt(selection.rangeCount - 1).collapsed;

    if (collapsed) {
        return { anchor, collapsed };
    }

    const focusCoordinates = getNodeCoordinates(selection.focusNode!);
    const focus = { coordinates: focusCoordinates, offset: selection.focusOffset };
    const order = compareLocations(anchor, focus);

    const direction = order === Order.GreaterThan ? "backward" : "forward";

    return {
        anchor,
        focus,
        collapsed,
        direction,
    };
}
