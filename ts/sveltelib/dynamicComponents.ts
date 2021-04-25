// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponentDev } from "svelte/internal";

export interface DynamicSvelteComponent<
    T extends typeof SvelteComponentDev = typeof SvelteComponentDev
> {
    component: T;
    [k: string]: unknown;
}

export const dynamicComponent = <
    Comp extends typeof SvelteComponentDev,
    DefaultProps = NonNullable<ConstructorParameters<Comp>[0]["props"]>
>(
    component: Comp
) => <Props = DefaultProps>(props: Props): DynamicSvelteComponent<Comp> & Props => {
    return { component, ...props };
};

import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";
import LabelButton from "./LabelButton.svelte";
import type { LabelButtonProps } from "./LabelButton";

import DropdownMenu from "./DropdownMenu.svelte";
import type { DropdownMenuProps } from "./DropdownMenu";
import DropdownItem from "./DropdownItem.svelte";
import type { DropdownItemProps } from "./DropdownItem";
import DropdownDivider from "./DropdownDivider.svelte";

export const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(
    ButtonGroup
);
export const labelButton = dynamicComponent<typeof LabelButton, LabelButtonProps>(
    LabelButton
);

export const dropdownMenu = dynamicComponent<typeof DropdownMenu, DropdownMenuProps>(
    DropdownMenu
);
export const dropdownItem = dynamicComponent<typeof DropdownItem, DropdownItemProps>(
    DropdownItem
);
export const dropdownDivider = dynamicComponent<typeof DropdownDivider, Record<string, never>>(
    DropdownDivider
);
