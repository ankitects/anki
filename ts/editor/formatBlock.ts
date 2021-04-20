// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import ButtonGroup from "editor-toolbar/ButtonGroup.svelte";
import type { ButtonGroupProps } from "editor-toolbar/ButtonGroup";
import ButtonDropdown from "editor-toolbar/ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "editor-toolbar/ButtonDropdown";
import WithDropdownMenu from "editor-toolbar/WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "editor-toolbar/WithDropdownMenu";

import CommandIconButton from "editor-toolbar/CommandIconButton.svelte";
import type { CommandIconButtonProps } from "editor-toolbar/CommandIconButton";
import IconButton from "editor-toolbar/IconButton.svelte";
import type { IconButtonProps } from "editor-toolbar/IconButton";

import type { EditingArea } from "./editingArea";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import { getListItem, getParagraph } from "./helpers";

import paragraphIcon from "./paragraph.svg";
import ulIcon from "./list-ul.svg";
import olIcon from "./list-ol.svg";
import listOptionsIcon from "./text-paragraph.svg";

import justifyFullIcon from "./justify.svg";
import justifyLeftIcon from "./text-left.svg";
import justifyRightIcon from "./text-right.svg";
import justifyCenterIcon from "./text-center.svg";

import indentIcon from "./text-indent-left.svg";
import outdentIcon from "./text-indent-right.svg";

const commandIconButton = dynamicComponent<
    typeof CommandIconButton,
    CommandIconButtonProps
>(CommandIconButton);

const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);

const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);
const buttonDropdown = dynamicComponent<typeof ButtonDropdown, ButtonDropdownProps>(
    ButtonDropdown
);

const withDropdownMenu = dynamicComponent<
    typeof WithDropdownMenu,
    WithDropdownMenuProps
>(WithDropdownMenu);

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

const toggleParagraph = (): void => {
    const currentField = document.activeElement as EditingArea;
    const paragraph = getParagraph(currentField.shadowRoot!);

    if (!paragraph) {
        document.execCommand("formatBlock", false, "p");
    } else {
        paragraph.insertAdjacentElement("beforeend", document.createElement("br"));
        paragraph.replaceWith(...paragraph.childNodes);
    }
};

const checkForParagraph = (): boolean => {
    const currentField = document.activeElement as EditingArea;
    return Boolean(getParagraph(currentField.shadowRoot!));
};

export function getFormatBlockMenus(): (DynamicSvelteComponent<typeof ButtonDropdown> &
    ButtonDropdownProps)[] {
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
        buttons: [
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
        buttons: [outdentButton, indentButton],
    });

    const formattingOptions = buttonDropdown({
        id: "listFormatting",
        buttons: [justifyGroup, indentationGroup],
    });

    return [formattingOptions];
}

export function getFormatBlockGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const paragraphButton = commandIconButton({
        icon: paragraphIcon,
        command: "formatBlock",
        onClick: toggleParagraph,
        onUpdate: checkForParagraph,
        tooltip: tr.editingParagraph(),
    });

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
        buttons: [paragraphButton, ulButton, olButton, listFormatting],
    });
}
