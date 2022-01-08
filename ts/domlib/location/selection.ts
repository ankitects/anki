// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getNodeCoordinates } from "./node";
import type { CaretLocation } from "./location";
import { compareLocations, Position } from "./location";
import { getSelection, getRange } from "../../lib/cross-browser";

export interface SelectionLocationCollapsed {
    readonly anchor: CaretLocation;
    readonly collapsed: true;
}

export interface SelectionLocationContent {
    readonly anchor: CaretLocation;
    readonly focus: CaretLocation;
    readonly collapsed: false;
    readonly direction: "forward" | "backward";
}

export type SelectionLocation = SelectionLocationCollapsed | SelectionLocationContent;

export function getSelectionLocation(base: Node): SelectionLocation | null {
    const selection = getSelection(base)!;
    const range = getRange(selection);

    if (!range) {
        return null;
    }

    const collapsed = range.collapsed;
    const anchorCoordinates = getNodeCoordinates(selection.anchorNode!, base);
    const anchor = { coordinates: anchorCoordinates, offset: selection.anchorOffset };

    if (collapsed) {
        return { anchor, collapsed };
    }

    const focusCoordinates = getNodeCoordinates(selection.focusNode!, base);
    const focus = { coordinates: focusCoordinates, offset: selection.focusOffset };
    const order = compareLocations(anchor, focus);

    const direction = order === Position.After ? "backward" : "forward";

    return {
        anchor,
        focus,
        collapsed,
        direction,
    };
}
