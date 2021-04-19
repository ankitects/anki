// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";
import ButtonDropdown from "./ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "./ButtonDropdown";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";

import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";
import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

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

const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);
const buttonDropdown = dynamicComponent<typeof ButtonDropdown, ButtonDropdownProps>(
    ButtonDropdown
);

const withDropdownMenu = dynamicComponent<
    typeof WithDropdownMenu,
    WithDropdownMenuProps
>(WithDropdownMenu);

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

    const outdentButton = commandIconButton({
        icon: outdentIcon,
        command: "outdent",
        tooltip: tr.editingOutdent(),
        activatable: false,
    });

    const indentButton = commandIconButton({
        icon: indentIcon,
        command: "indent",
        tooltip: tr.editingIndent(),
        activatable: false,
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

const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(IconButton);

export function getFormatBlockGroup(): DynamicSvelteComponent<typeof ButtonGroup> &
    ButtonGroupProps {
    const paragraphButton = commandIconButton({
        icon: paragraphIcon,
        command: "formatBlock",
        onClick: () => {
            document.execCommand("formatBlock", false, "p");
        },
        tooltip: tr.editingUnorderedList(),
        activatable: false,
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
