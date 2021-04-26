// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { dynamicComponent } from "sveltelib/dynamicComponents";

import RawButton from "./RawButton.svelte";

import IconButton from "./IconButton.svelte";
import type { IconButtonProps } from "./IconButton";
import CommandIconButton from "./CommandIconButton.svelte";
import type { CommandIconButtonProps } from "./CommandIconButton";
import ColorPicker from "./ColorPicker.svelte";
import type { ColorPickerProps } from "./ColorPicker";

import ButtonDropdown from "./ButtonDropdown.svelte";
import type { ButtonDropdownProps } from "./ButtonDropdown";
import WithDropdownMenu from "./WithDropdownMenu.svelte";
import type { WithDropdownMenuProps } from "./WithDropdownMenu";

import WithShortcut from "./WithShortcut.svelte";
import type { WithShortcutProps } from "./WithShortcut";

import WithLabel from "./WithLabel.svelte";
import type { WithLabelProps } from "./WithLabel";

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

export const buttonDropdown = dynamicComponent<
    typeof ButtonDropdown,
    ButtonDropdownProps
>(ButtonDropdown);

export const withDropdownMenu = dynamicComponent<
    typeof WithDropdownMenu,
    WithDropdownMenuProps
>(WithDropdownMenu);

export const withShortcut = dynamicComponent<typeof WithShortcut, WithShortcutProps>(
    WithShortcut
);

export const withLabel = dynamicComponent<typeof WithLabel, WithLabelProps>(WithLabel);
