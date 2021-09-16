// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getNodeCoordinates } from "./node";
import type { CaretLocation } from "./location";

interface RangeCoordinatesCollapsed {
    readonly start: CaretLocation;
    readonly collapsed: true;
}

interface RangeCoordinatesContent {
    readonly start: CaretLocation;
    readonly end: CaretLocation;
    readonly collapsed: false;
}

export type RangeCoordinates = RangeCoordinatesCollapsed | RangeCoordinatesContent;

export function getRangeCoordinates(range: Range): RangeCoordinates {
    const root = range.commonAncestorContainer.getRootNode();

    const startCoordinates = getNodeCoordinates(range.startContainer, root);
    const start = { coordinates: startCoordinates, offset: range.startOffset };
    const collapsed = range.collapsed;

    if (collapsed) {
        return { start, collapsed };
    }

    const endCoordinates = getNodeCoordinates(range.endContainer, root);
    const end = { coordinates: endCoordinates, offset: range.endOffset };

    return { start, end, collapsed };
}
