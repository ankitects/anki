// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

import {
    mdiCursorDefaultOutline,
    mdiEllipseOutline,
    mdiRectangleOutline,
    mdiTextBox,
    mdiVectorPolygonVariant,
} from "../icons";
import {
    cursorKeyCombination,
    ellipseKeyCombination,
    polygonKeyCombination,
    rectangleKeyCombination,
    textKeyCombination,
} from "./shortcuts";

export const tools = [
    {
        id: "cursor",
        icon: mdiCursorDefaultOutline,
        tooltip: tr.editingImageOcclusionSelectTool,
        shortcut: cursorKeyCombination,
    },
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
    {
        id: "draw-text",
        icon: mdiTextBox,
        tooltip: tr.editingImageOcclusionTextTool,
        shortcut: textKeyCombination,
    },
];
