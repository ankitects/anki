// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";
import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import type ButtonDropdown from "./ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "./ButtonDropdown";

import { DynamicSvelteComponent, dynamicComponent } from "sveltelib/dynamicComponent";
import * as tr from "anki/i18n";

import ulIcon from "./list-ul.svg";
import olIcon from "./list-ol.svg";
import listOptionsIcon from "./text-paragraph.svg";

import justifyIcon from "./justify.svg";
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

    return buttonGroup({
        id: "formatInline",
        buttons: [ulButton, olButton, listFormattingButton],
    });
}

export function getFormatBlockMenus(): (DynamicSvelteComponent<typeof ButtonDropdown> &
    ButtonDropdownProps)[] {
    return [];
}
