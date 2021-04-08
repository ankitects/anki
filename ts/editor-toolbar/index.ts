import type { SvelteComponent } from "svelte";
import { writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import { notetypeGroup } from "./notetype";
import { formatGroup } from "./format";
import { colorGroup } from "./color";
import { templateGroup, templateMenus } from "./template";

// @ts-expect-error
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";

const defaultButtons = [notetypeGroup, formatGroup, colorGroup, templateGroup];
const defaultMenus = [...templateMenus];

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;

    buttons = writable(defaultButtons);
    menus = writable(defaultMenus);

    connectedCallback(): void {
        setupI18n({ modules: [ModuleName.EDITING] }).then(() => {
            this.component = new EditorToolbarSvelte({
                target: this,
                props: {
                    buttons: this.buttons,
                    menus: this.menus,
                    nightMode: checkNightMode(),
                },
            });
        });
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for add-ons */
export { default as LabelButton } from "./LabelButton.svelte";
export { default as IconButton } from "./IconButton.svelte";
export { default as CommandIconButton } from "./CommandIconButton.svelte";
export { default as SelectButton } from "./SelectButton.svelte";

export { default as DropdownMenu } from "./DropdownMenu.svelte";
export { default as DropdownItem } from "./DropdownItem.svelte";
export { default as ButtonDropdown } from "./DropdownMenu.svelte";
export { default as WithDropdownMenu } from "./WithDropdownMenu.svelte";
