// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    mdiCursorDefaultOutline,
    mdiEllipseOutline,
    mdiMagnifyScan,
    mdiRectangleOutline,
    mdiTextBox,
    mdiVectorPolygonVariant,
} from "../icons";

export const tools = [
    {
        id: "cursor",
        icon: mdiCursorDefaultOutline,
    },
    {
        id: "magnify",
        icon: mdiMagnifyScan,
    },
    {
        id: "draw-rectangle",
        icon: mdiRectangleOutline,
    },
    {
        id: "draw-ellipse",
        icon: mdiEllipseOutline,
    },
    {
        id: "draw-polygon",
        icon: mdiVectorPolygonVariant,
    },
    {
        id: "draw-text",
        icon: mdiTextBox,
    },
];
