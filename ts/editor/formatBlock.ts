// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { IterableToolbarItem } from "sveltelib/types";
import type { EditingArea } from "./editingArea";

import * as tr from "lib/i18n";
import { buttonGroup } from "sveltelib/dynamicComponents";
import {
    commandIconButton,
    iconButton,
    buttonDropdown,
    withDropdownMenu,
} from "editor-toolbar/dynamicComponents";

import { getListItem } from "./helpers";

import ulIcon from "./list-ul.svg";
import olIcon from "./list-ol.svg";
import listOptionsIcon from "./text-paragraph.svg";

import justifyFullIcon from "./justify.svg";
import justifyLeftIcon from "./text-left.svg";
import justifyRightIcon from "./text-right.svg";
import justifyCenterIcon from "./text-center.svg";

import indentIcon from "./text-indent-left.svg";
import outdentIcon from "./text-indent-right.svg";

const outdentListItem = () => {
    const currentField = document.activeElement as EditingArea;
    if (getListItem(currentField.shadowRoot!)) {
        document.execCommand("outdent");
    }
};

const indentListItem = () => {
    const currentField = document.activeElement as EditingArea;
    if (getListItem(currentField.shadowRoot!)) {
        document.execCommand("indent");
    }
};

export function getFormatBlockMenus(): IterableToolbarItem[] {
    const justifyLeftButton = commandIconButton({
        icon: justifyLeftIcon,
        command: "justifyLeft",
        tooltip: tr.editingAlignLeft(),
    });

    const justifyCenterButton = commandIconButton({
        icon: justifyCenterIcon,
        command: "justifyCenter",
        tooltip: tr.editingCenter(),
    });

    const justifyRightButton = commandIconButton({
        icon: justifyRightIcon,
        command: "justifyRight",
        tooltip: tr.editingAlignRight(),
    });

    const justifyFullButton = commandIconButton({
        icon: justifyFullIcon,
        command: "justifyFull",
        tooltip: tr.editingJustify(),
    });

    const justifyGroup = buttonGroup({
        id: "justify",
        items: [
            justifyLeftButton,
            justifyCenterButton,
            justifyRightButton,
            justifyFullButton,
        ],
    });

    const outdentButton = iconButton({
        icon: outdentIcon,
        onClick: outdentListItem,
        tooltip: tr.editingOutdent(),
    });

    const indentButton = iconButton({
        icon: indentIcon,
        onClick: indentListItem,
        tooltip: tr.editingIndent(),
    });

    const indentationGroup = buttonGroup({
        id: "indentation",
        items: [outdentButton, indentButton],
    });

    const formattingOptions = buttonDropdown({
        id: "listFormatting",
        items: [justifyGroup, indentationGroup],
    });

    return [formattingOptions];
}

export function getFormatBlockGroup(): IterableToolbarItem {
    const ulButton = commandIconButton({
        icon: ulIcon,
        command: "insertUnorderedList",
        tooltip: tr.editingUnorderedList(),
    });

    const olButton = commandIconButton({
        icon: olIcon,
        command: "insertOrderedList",
        tooltip: tr.editingOrderedList(),
    });

    const listFormattingButton = iconButton({
        icon: listOptionsIcon,
    });

    const listFormatting = withDropdownMenu({
        button: listFormattingButton,
        menuId: "listFormatting",
    });

    return buttonGroup({
        id: "blockFormatting",
        items: [ulButton, olButton, listFormatting],
    });
}
