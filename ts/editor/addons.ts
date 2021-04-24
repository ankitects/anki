// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { DynamicSvelteComponent } from "sveltelib/dynamicComponent";

import {
    buttonGroup,
    rawButton,
    labelButton,
    iconButton,
    commandIconButton,
    selectButton,
    dropdownMenu,
    dropdownItem,
    buttonDropdown,
    withDropdownMenu,
    withLabel,
} from "editor-toolbar/dynamicComponents";

export const editorToolbar: Record<
    string,
    (props: Record<string, unknown>) => DynamicSvelteComponent
> = {
    buttonGroup,
    rawButton,
    labelButton,
    iconButton,
    commandIconButton,
    selectButton,

    dropdownMenu,
    dropdownItem,
    buttonDropdown,
    withDropdownMenu,
    withLabel,
};
