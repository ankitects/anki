// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import hsplitIcon_ from "@mdi/svg/svg/arrow-split-horizontal.svg";
import vsplitIcon_ from "@mdi/svg/svg/arrow-split-vertical.svg";
import checkCircle_ from "@mdi/svg/svg/check-circle.svg";
import chevronDown_ from "@mdi/svg/svg/chevron-down.svg";
import chevronLeft_ from "@mdi/svg/svg/chevron-left.svg";
import chevronRight_ from "@mdi/svg/svg/chevron-right.svg";
import chevronUp_ from "@mdi/svg/svg/chevron-up.svg";
import closeBox_ from "@mdi/svg/svg/close-box.svg";
import dotsIcon_ from "@mdi/svg/svg/dots-vertical.svg";
import horizontalHandle_ from "@mdi/svg/svg/drag-horizontal.svg";
import verticalHandle_ from "@mdi/svg/svg/drag-vertical.svg";
import infoCircle_ from "@mdi/svg/svg/help-circle.svg";
import magnifyIcon_ from "@mdi/svg/svg/magnify.svg";
import newBox_ from "@mdi/svg/svg/new-box.svg";
import tagIcon_ from "@mdi/svg/svg/tag-outline.svg";
import addTagIcon_ from "@mdi/svg/svg/tag-plus-outline.svg";
import updateIcon_ from "@mdi/svg/svg/update.svg";
import revertIcon_ from "bootstrap-icons/icons/arrow-counterclockwise.svg";
import arrowLeftIcon_ from "bootstrap-icons/icons/arrow-left.svg";
import arrowRightIcon_ from "bootstrap-icons/icons/arrow-right.svg";
import minusIcon_ from "bootstrap-icons/icons/dash-lg.svg";
import exclamationIcon_ from "bootstrap-icons/icons/exclamation-circle.svg";
import plusIcon_ from "bootstrap-icons/icons/plus-lg.svg";

/* @__PURE__ */
export function imageLink(url: string): string {
    if (import.meta.env) {
        // in vite, we need to create the image link ourselves
        return `<img src="${url}" />`;
    } else {
        // in legacy esbuild builds, the url is an image link already
        return url;
    }
}

export const arrowLeftIcon = imageLink(arrowLeftIcon_);
export const arrowRightIcon = imageLink(arrowRightIcon_);
export const checkCircle = imageLink(checkCircle_);
export const chevronDown = imageLink(chevronDown_);
export const chevronLeft = imageLink(chevronLeft_);
export const chevronRight = imageLink(chevronRight_);
export const chevronUp = imageLink(chevronUp_);
export const closeBox = imageLink(closeBox_);
export const exclamationIcon = imageLink(exclamationIcon_);
export const horizontalHandle = imageLink(horizontalHandle_);
export const hsplitIcon = imageLink(hsplitIcon_);
export const infoCircle = imageLink(infoCircle_);
export const magnifyIcon = imageLink(magnifyIcon_);
export const minusIcon = imageLink(minusIcon_);
export const newBox = imageLink(newBox_);
export const plusIcon = imageLink(plusIcon_);
export const revertIcon = imageLink(revertIcon_);
export const updateIcon = imageLink(updateIcon_);
export const verticalHandle = imageLink(verticalHandle_);
export const vsplitIcon = imageLink(vsplitIcon_);
export const dotsIcon = imageLink(dotsIcon_);
export const tagIcon = imageLink(tagIcon_);
export const addTagIcon = imageLink(addTagIcon_);
