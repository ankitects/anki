// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import CheckCircle_ from "@mdi/svg/svg/check-circle.svg?component";
import checkCircle_ from "@mdi/svg/svg/check-circle.svg?url";
import ChevronDown_ from "@mdi/svg/svg/chevron-down.svg?component";
import chevronDown_ from "@mdi/svg/svg/chevron-down.svg?url";
import ChevronUp_ from "@mdi/svg/svg/chevron-up.svg?component";
import chevronUp_ from "@mdi/svg/svg/chevron-up.svg?url";
import CloseBox_ from "@mdi/svg/svg/close-box.svg?component";
import closeBox_ from "@mdi/svg/svg/close-box.svg?url";
import DotsIcon_ from "@mdi/svg/svg/dots-vertical.svg?component";
import dotsIcon_ from "@mdi/svg/svg/dots-vertical.svg?url";
import HorizontalHandle_ from "@mdi/svg/svg/drag-horizontal.svg?component";
import horizontalHandle_ from "@mdi/svg/svg/drag-horizontal.svg?url";
import VerticalHandle_ from "@mdi/svg/svg/drag-vertical.svg?component";
import verticalHandle_ from "@mdi/svg/svg/drag-vertical.svg?url";
import InfoCircle_ from "@mdi/svg/svg/help-circle.svg?component";
import infoCircle_ from "@mdi/svg/svg/help-circle.svg?url";
import MagnifyIcon_ from "@mdi/svg/svg/magnify.svg?component";
import magnifyIcon_ from "@mdi/svg/svg/magnify.svg?url";
import NewBox_ from "@mdi/svg/svg/new-box.svg?component";
import newBox_ from "@mdi/svg/svg/new-box.svg?url";
import TagIcon_ from "@mdi/svg/svg/tag-outline.svg?component";
import tagIcon_ from "@mdi/svg/svg/tag-outline.svg?url";
import AddTagIcon_ from "@mdi/svg/svg/tag-plus-outline.svg?component";
import addTagIcon_ from "@mdi/svg/svg/tag-plus-outline.svg?url";
import UpdateIcon_ from "@mdi/svg/svg/update.svg?component";
import updateIcon_ from "@mdi/svg/svg/update.svg?url";
import RevertIcon_ from "bootstrap-icons/icons/arrow-counterclockwise.svg?component";
import revertIcon_ from "bootstrap-icons/icons/arrow-counterclockwise.svg?url";
import ArrowLeftIcon_ from "bootstrap-icons/icons/arrow-left.svg?component";
import arrowLeftIcon_ from "bootstrap-icons/icons/arrow-left.svg?url";
import ArrowRightIcon_ from "bootstrap-icons/icons/arrow-right.svg?component";
import arrowRightIcon_ from "bootstrap-icons/icons/arrow-right.svg?url";
import MinusIcon_ from "bootstrap-icons/icons/dash-lg.svg?component";
import minusIcon_ from "bootstrap-icons/icons/dash-lg.svg?url";
import ExclamationIcon_ from "bootstrap-icons/icons/exclamation-circle.svg?component";
import exclamationIcon_ from "bootstrap-icons/icons/exclamation-circle.svg?url";
import PlusIcon_ from "bootstrap-icons/icons/plus-lg.svg?component";
import plusIcon_ from "bootstrap-icons/icons/plus-lg.svg?url";

/* @__PURE__ */
export function imageLink(url: string): string {
    if (import.meta.env) {
        // this actually returns a Svelte component, so the current typing is a lie
        return url;
    } else {
        // in legacy esbuild builds, the url is an image link already
        return url;
    }
}

export const checkCircle = { url: checkCircle_, component: CheckCircle_ };
export const chevronDown = { url: chevronDown_, component: ChevronDown_ };
export const chevronUp = { url: chevronUp_, component: ChevronUp_ };
export const closeBox = { url: closeBox_, component: CloseBox_ };
export const dotsIcon = { url: dotsIcon_, component: DotsIcon_ };
export const horizontalHandle = { url: horizontalHandle_, component: HorizontalHandle_ };
export const verticalHandle = { url: verticalHandle_, component: VerticalHandle_ };
export const infoCircle = { url: infoCircle_, component: InfoCircle_ };
export const magnifyIcon = { url: magnifyIcon_, component: MagnifyIcon_ };
export const newBox = { url: newBox_, component: NewBox_ };
export const tagIcon = { url: tagIcon_, component: TagIcon_ };
export const addTagIcon = { url: addTagIcon_, component: AddTagIcon_ };
export const updateIcon = { url: updateIcon_, component: UpdateIcon_ };
export const revertIcon = { url: revertIcon_, component: RevertIcon_ };
export const arrowLeftIcon = { url: arrowLeftIcon_, component: ArrowLeftIcon_ };
export const arrowRightIcon = { url: arrowRightIcon_, component: ArrowRightIcon_ };
export const minusIcon = { url: minusIcon_, component: MinusIcon_ };
export const exclamationIcon = { url: exclamationIcon_, component: ExclamationIcon_ };
export const plusIcon = { url: plusIcon_, component: PlusIcon_ };
