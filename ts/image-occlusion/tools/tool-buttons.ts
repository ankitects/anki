// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";

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
        tooltip: tr.editingImageOcclusionSelectTool,
    },
    {
        id: "magnify",
        icon: mdiMagnifyScan,
        tooltip: tr.editingImageOcclusionZoomTool,
    },
    {
        id: "draw-rectangle",
        icon: mdiRectangleOutline,
        tooltip: tr.editingImageOcclusionRectangleTool,
    },
    {
        id: "draw-ellipse",
        icon: mdiEllipseOutline,
        tooltip: tr.editingImageOcclusionEllipseTool,
    },
    {
        id: "draw-polygon",
        icon: mdiVectorPolygonVariant,
        tooltip: tr.editingImageOcclusionPolygonTool,
    },
    {
        id: "draw-text",
        icon: mdiTextBox,
        tooltip: tr.editingImageOcclusionTextTool,
    },
];
