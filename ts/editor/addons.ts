// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponents";

import {
    dropdownMenu,
    dropdownItem,
    labelButton,
    buttonGroup,
} from "sveltelib/dynamicComponents";

import {
    rawButton,
    iconButton,
    commandIconButton,
    selectButton,
    buttonDropdown,
    withDropdownMenu,
    withLabel,
} from "editor-toolbar/dynamicComponents";

export const editorToolbar: Record<
    string,
    (props: Record<string, unknown>) => DynamicSvelteComponent
> = {
    dropdownMenu,
    dropdownItem,

    buttonGroup,
    rawButton,
    labelButton,
    iconButton,
    commandIconButton,
    selectButton,

    buttonDropdown,
    withDropdownMenu,
    withLabel,
};
