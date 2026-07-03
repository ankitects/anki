// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

import {
    mdiCursorDefaultOutline,
    mdiEllipseOutline,
    mdiFormatColorFill,
    mdiRectangleOutline,
    mdiTextBox,
    mdiVectorPolygonVariant,
} from "$lib/components/icons";

import {
    cursorKeyCombination,
    ellipseKeyCombination,
    fillKeyCombination,
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
    {
        id: "fill-mask",
        icon: mdiFormatColorFill,
        iconSizeMult: 1.4,
        tooltip: tr.editingImageOcclusionFillTool,
        shortcut: fillKeyCombination,
    },
] as const;

export type ActiveTool = typeof tools[number]["id"];
