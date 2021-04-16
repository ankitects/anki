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
        tooltip: "Justify to the left",
    });

    const justifyCenterButton = commandIconButton({
        icon: justifyCenterIcon,
        command: "justifyCenter",
        tooltip: "Justify to the center",
    });

    const justifyFullButton = commandIconButton({
        icon: justifyFullIcon,
        command: "justifyFull",
        tooltip: "Justify full",
    });

    const justifyRightButton = commandIconButton({
        icon: justifyRightIcon,
        command: "justifyRight",
        tooltip: "Justify to the right",
    });

    const justifyGroup = buttonGroup({
        id: "justify",
        buttons: [
            justifyLeftButton,
            justifyCenterButton,
            justifyFullButton,
            justifyRightButton,
        ],
    });

    const indentButton = commandIconButton({
        icon: indentIcon,
        command: "indent",
        tooltip: "Indent selection",
    });

    const outdentButton = commandIconButton({
        icon: outdentIcon,
        command: "outdent",
        tooltip: "Outdent selection",
    });

    const indentationGroup = buttonGroup({
        id: "indentation",
        buttons: [indentButton, outdentButton],
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
    const ulButton = commandIconButton({
        icon: ulIcon,
        command: "insertUnorderedList",
        tooltip: "Insert unordered list",
    });

    const olButton = commandIconButton({
        icon: olIcon,
        command: "insertOrderedList",
        tooltip: "Insert ordered list",
    });

    const listFormattingButton = iconButton({
        icon: listOptionsIcon,
        tooltip: "More list options",
    });

    const listFormatting = withDropdownMenu({
        button: listFormattingButton,
        menuId: "listFormatting",
    });

    return buttonGroup({
        id: "formatInline",
        buttons: [ulButton, olButton, listFormatting],
    });
}
