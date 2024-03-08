// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";

import {
    mdiArrowTopRightThin,
    mdiCursorDefaultOutline,
    mdiEllipseOutline,
    mdiGesture,
    mdiMagnifyScan,
    mdiRectangleOutline,
    mdiTextBox,
    mdiVectorPolygonVariant,
} from "../icons";
import {
    cursorKeyCombination,
    ellipseKeyCombination,
    lineKeyCombination,
    magnifyKeyCombination,
    pathKeyCombination,
    polygonKeyCombination,
    rectangleKeyCombination,
    textKeyCombination,
} from "./shortcuts";

export const toolsCursor = [
    {
        id: "cursor",
        icon: mdiCursorDefaultOutline,
        tooltip: tr.editingImageOcclusionSelectTool,
        shortcut: cursorKeyCombination,
    },
    {
        id: "magnify",
        icon: mdiMagnifyScan,
        tooltip: tr.editingImageOcclusionZoomTool,
        shortcut: magnifyKeyCombination,
    },
];

export const toolsShape = [
    {
        id: "draw-rectangle",
        icon: mdiRectangleOutline,
        tooltip: tr.editingImageOcclusionRectangleTool,
        shortcut: rectangleKeyCombination,
    },
    {
        id: "draw-ellipse",
        icon: mdiEllipseOutline,
        tooltip: tr.editingImageOcclusionEllipseTool,
        shortcut: ellipseKeyCombination,
    },
    {
        id: "draw-polygon",
        icon: mdiVectorPolygonVariant,
        tooltip: tr.editingImageOcclusionPolygonTool,
        shortcut: polygonKeyCombination,
    },
];

export const toolsAnnotation = [
    {
        id: "draw-text",
        icon: mdiTextBox,
        tooltip: tr.editingImageOcclusionTextTool,
        shortcut: textKeyCombination,
    },
    {
        id: "draw-line",
        icon: mdiArrowTopRightThin,
        tooltip: tr.editingImageOcclusionLineTool,
        shortcut: lineKeyCombination,
    },
    {
        id: "draw-path",
        icon: mdiGesture,
        tooltip: tr.editingImageOcclusionPathTool,
        shortcut: pathKeyCombination,
    },
];
