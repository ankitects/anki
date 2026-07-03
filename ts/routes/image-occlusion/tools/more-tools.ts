// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

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
    mdiSelectAll,
    mdiUngroup,
    mdiZoomIn,
    mdiZoomOut,
    mdiZoomReset,
} from "$lib/components/icons";

import { deleteItem, duplicateItem, groupShapes, selectAllShapes, unGroupShapes } from "./lib";
import {
    alignBottomKeyCombination,
    alignHorizontalCenterKeyCombination,
    alignLeftKeyCombination,
    alignRightKeyCombination,
    alignTopKeyCombination,
    alignVerticalCenterKeyCombination,
    deleteKeyCombination,
    duplicateKeyCombination,
    groupKeyCombination,
    selectAllKeyCombination,
    ungroupKeyCombination,
    zoomInKeyCombination,
    zoomOutKeyCombination,
    zoomResetKeyCombination,
} from "./shortcuts";
import {
    alignBottom,
    alignHorizontalCenter,
    alignLeft,
    alignRight,
    alignTop,
    alignVerticalCenter,
} from "./tool-aligns";
import { zoomIn, zoomOut, zoomReset } from "./tool-zoom";

export const groupUngroupTools = [
    {
        name: "group",
        icon: mdiGroup,
        action: groupShapes,
        tooltip: tr.editingImageOcclusionGroup,
        shortcut: groupKeyCombination,
    },
    {
        name: "ungroup",
        icon: mdiUngroup,
        action: unGroupShapes,
        tooltip: tr.editingImageOcclusionUngroup,
        shortcut: ungroupKeyCombination,
    },
    {
        name: "select-all",
        icon: mdiSelectAll,
        action: selectAllShapes,
        tooltip: tr.editingImageOcclusionSelectAll,
        shortcut: selectAllKeyCombination,
    },
];

export const deleteDuplicateTools = [
    {
        name: "delete",
        icon: mdiDeleteOutline,
        action: deleteItem,
        tooltip: tr.editingImageOcclusionDelete,
        shortcut: deleteKeyCombination,
    },
    {
        name: "duplicate",
        icon: mdiCopy,
        action: duplicateItem,
        tooltip: tr.editingImageOcclusionDuplicate,
        shortcut: duplicateKeyCombination,
    },
];

export const zoomTools = [
    {
        name: "zoomOut",
        icon: mdiZoomOut,
        action: zoomOut,
        tooltip: tr.editingImageOcclusionZoomOut,
        shortcut: zoomOutKeyCombination,
    },
    {
        name: "zoomIn",
        icon: mdiZoomIn,
        action: zoomIn,
        tooltip: tr.editingImageOcclusionZoomIn,
        shortcut: zoomInKeyCombination,
    },
    {
        name: "zoomReset",
        icon: mdiZoomReset,
        action: zoomReset,
        tooltip: tr.editingImageOcclusionZoomReset,
        shortcut: zoomResetKeyCombination,
    },
];

export const alignTools = [
    {
        id: 1,
        icon: mdiAlignHorizontalLeft,
        action: alignLeft,
        tooltip: tr.editingImageOcclusionAlignLeft,
        shortcut: alignLeftKeyCombination,
    },
    {
        id: 2,
        icon: mdiAlignHorizontalCenter,
        action: alignHorizontalCenter,
        tooltip: tr.editingImageOcclusionAlignHCenter,
        shortcut: alignHorizontalCenterKeyCombination,
    },
    {
        id: 3,
        icon: mdiAlignHorizontalRight,
        action: alignRight,
        tooltip: tr.editingImageOcclusionAlignRight,
        shortcut: alignRightKeyCombination,
    },
    {
        id: 4,
        icon: mdiAlignVerticalTop,
        action: alignTop,
        tooltip: tr.editingImageOcclusionAlignTop,
        shortcut: alignTopKeyCombination,
    },
    {
        id: 5,
        icon: mdiAlignVerticalCenter,
        action: alignVerticalCenter,
        tooltip: tr.editingImageOcclusionAlignVCenter,
        shortcut: alignVerticalCenterKeyCombination,
    },
    {
        id: 6,
        icon: mdiAlignVerticalBottom,
        action: alignBottom,
        tooltip: tr.editingImageOcclusionAlignBottom,
        shortcut: alignBottomKeyCombination,
    },
];
