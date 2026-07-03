// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { CaretLocation } from "./location";
import { getNodeCoordinates } from "./node";

interface RangeCoordinatesCollapsed {
    readonly start: CaretLocation;
    readonly collapsed: true;
}

export interface RangeCoordinatesContent {
    readonly start: CaretLocation;
    readonly end: CaretLocation;
    readonly collapsed: false;
}

export type RangeCoordinates = RangeCoordinatesCollapsed | RangeCoordinatesContent;

export function getRangeCoordinates(range: Range, base: Node): RangeCoordinates {
    const startCoordinates = getNodeCoordinates(base, range.startContainer);
    const start = { coordinates: startCoordinates, offset: range.startOffset };
    const collapsed = range.collapsed;

    if (collapsed) {
        return { start, collapsed };
    }

    const endCoordinates = getNodeCoordinates(base, range.endContainer);
    const end = { coordinates: endCoordinates, offset: range.endOffset };

    return { start, end, collapsed };
}
