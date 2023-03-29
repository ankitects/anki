// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    mdiAlignHorizontalCenter,
    mdiAlignHorizontalLeft,
    mdiAlignHorizontalRight,
    mdiAlignVerticalBottom,
    mdiAlignVerticalCenter,
    mdiAlignVerticalTop,
    mdiCopy,
    mdiDeleteOutline,
    mdiGroup,
    mdiUngroup,
    mdiZoomIn,
    mdiZoomOut,
    mdiZoomReset,
} from "../icons";
import { deleteItem, duplicateItem, groupShapes, unGroupShapes, zoomIn, zoomOut, zoomReset } from "./lib";
import {
    alignBottom,
    alignHorizontalCenter,
    alignLeft,
    alignRight,
    alignTop,
    alignVerticalCenter,
} from "./tool-aligns";

export const groupUngroupTools = [
    {
        name: "group",
        icon: mdiGroup,
        action: groupShapes,
    },
    {
        name: "ungroup",
        icon: mdiUngroup,
        action: unGroupShapes,
    },
];

export const deleteDuplicateTools = [
    {
        name: "delete",
        icon: mdiDeleteOutline,
        action: deleteItem,
    },
    {
        name: "duplicate",
        icon: mdiCopy,
        action: duplicateItem,
    },
];

export const zoomTools = [
    {
        name: "zoomOut",
        icon: mdiZoomOut,
        action: zoomOut,
    },
    {
        name: "zoomIn",
        icon: mdiZoomIn,
        action: zoomIn,
    },
    {
        name: "zoomReset",
        icon: mdiZoomReset,
        action: zoomReset,
    },
];

export const alignTools = [
    {
        id: 1,
        icon: mdiAlignHorizontalLeft,
        action: alignLeft,
    },
    {
        id: 2,
        icon: mdiAlignHorizontalCenter,
        action: alignHorizontalCenter,
    },
    {
        id: 3,
        icon: mdiAlignHorizontalRight,
        action: alignRight,
    },
    {
        id: 4,
        icon: mdiAlignVerticalTop,
        action: alignTop,
    },
    {
        id: 5,
        icon: mdiAlignVerticalCenter,
        action: alignVerticalCenter,
    },
    {
        id: 6,
        icon: mdiAlignVerticalBottom,
        action: alignBottom,
    },
];
