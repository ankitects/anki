// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@tslib/ftl";

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
        tooltip: tr.editingImageOcclusionGroup,
    },
    {
        name: "ungroup",
        icon: mdiUngroup,
        action: unGroupShapes,
        tooltip: tr.editingImageOcclusionUngroup,
    },
];

export const deleteDuplicateTools = [
    {
        name: "delete",
        icon: mdiDeleteOutline,
        action: deleteItem,
        tooltip: tr.editingImageOcclusionDelete,
    },
    {
        name: "duplicate",
        icon: mdiCopy,
        action: duplicateItem,
        tooltip: tr.editingImageOcclusionDuplicate,
    },
];

export const zoomTools = [
    {
        name: "zoomOut",
        icon: mdiZoomOut,
        action: zoomOut,
        tooltip: tr.editingImageOcclusionZoomOut,
    },
    {
        name: "zoomIn",
        icon: mdiZoomIn,
        action: zoomIn,
        tooltip: tr.editingImageOcclusionZoomIn,
    },
    {
        name: "zoomReset",
        icon: mdiZoomReset,
        action: zoomReset,
        tooltip: tr.editingImageOcclusionZoomReset,
    },
];

export const alignTools = [
    {
        id: 1,
        icon: mdiAlignHorizontalLeft,
        action: alignLeft,
        tooltip: tr.editingImageOcclusionAlignLeft,
    },
    {
        id: 2,
        icon: mdiAlignHorizontalCenter,
        action: alignHorizontalCenter,
        tooltip: tr.editingImageOcclusionAlignHCenter,
    },
    {
        id: 3,
        icon: mdiAlignHorizontalRight,
        action: alignRight,
        tooltip: tr.editingImageOcclusionAlignRight,
    },
    {
        id: 4,
        icon: mdiAlignVerticalTop,
        action: alignTop,
        tooltip: tr.editingImageOcclusionAlignTop,
    },
    {
        id: 5,
        icon: mdiAlignVerticalCenter,
        action: alignVerticalCenter,
        tooltip: tr.editingImageOcclusionAlignVCenter,
    },
    {
        id: 6,
        icon: mdiAlignVerticalBottom,
        action: alignBottom,
        tooltip: tr.editingImageOcclusionAlignBottom,
    },
];
