// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import RawButton from "./RawButton.svelte";
import LabelButton from "./LabelButton.svelte";
import type { LabelButtonProps } from "./LabelButton";
import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";
import ColorPicker from "./ColorPicker.svelte";
import type { ColorPickerProps } from "./ColorPicker";
import SelectButton from "./SelectButton.svelte";
import type { SelectButtonProps } from "./SelectButton";
import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import ButtonDropdown from "./ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "./ButtonDropdown";
import DropdownMenu from "./DropdownMenu.svelte";
import type { DropdownMenuProps } from "./DropdownMenu";
import DropdownItem from "./DropdownItem.svelte";
import type { DropdownItemProps } from "./DropdownItem";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";

import WithShortcut from "./WithShortcut.svelte";
import type { WithShortcutProps } from "./WithShortcut";

import { dynamicComponent } from "sveltelib/dynamicComponent";

export const rawButton = dynamicComponent<typeof RawButton, { html: string }>(
    RawButton
);
export const labelButton = dynamicComponent<typeof LabelButton, LabelButtonProps>(
    LabelButton
);
export const iconButton = dynamicComponent<typeof IconButton, IconButtonProps>(
    IconButton
);
export const commandIconButton = dynamicComponent<
    typeof CommandIconButton,
    CommandIconButtonProps
>(CommandIconButton);
export const colorPicker = dynamicComponent<typeof ColorPicker, ColorPickerProps>(
    ColorPicker
);
export const selectButton = dynamicComponent<typeof SelectButton, SelectButtonProps>(
    SelectButton
);

export const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(
    ButtonGroup
);
export const buttonDropdown = dynamicComponent<
    typeof ButtonDropdown,
    ButtonDropdownProps
>(ButtonDropdown);

export const dropdownMenu = dynamicComponent<typeof DropdownMenu, DropdownMenuProps>(
    DropdownMenu
);
export const dropdownItem = dynamicComponent<typeof DropdownItem, DropdownItemProps>(
    DropdownItem
);

export const withDropdownMenu = dynamicComponent<
    typeof WithDropdownMenu,
    WithDropdownMenuProps
>(WithDropdownMenu);

export const withShortcut = dynamicComponent<typeof WithShortcut, WithShortcutProps>(
    WithShortcut
);
