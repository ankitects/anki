// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import RawButton from "./RawButton.svelte";
import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";
import ColorPicker from "./ColorPicker.svelte";
import type { ColorPickerProps } from "./ColorPicker";
import SelectButton from "./SelectButton.svelte";
import type { SelectButtonProps } from "./SelectButton";

import ButtonDropdown from "./ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "./ButtonDropdown";
import DropdownMenu from "./DropdownMenu.svelte";
import type { DropdownMenuProps } from "./DropdownMenu";
import DropdownItem from "./DropdownItem.svelte";
import type { DropdownItemProps } from "./DropdownItem";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";

import WithLabel from "./WithLabel.svelte";
import type { WithLabelProps } from "./WithLabel";

import { dynamicComponent } from "sveltelib/dynamicComponent";

export const rawButton = dynamicComponent<typeof RawButton, { html: string }>(
    RawButton
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

export const withLabel = dynamicComponent<typeof WithLabel, WithLabelProps>(WithLabel);
