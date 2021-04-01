import type { SvelteComponent } from "svelte";
import { writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import { notetypeButtons } from "./notetype";
import { formatButtons } from "./format";
import { colorButtons } from "./color";
import { templateButtons, templateMenus } from "./template";

// @ts-ignore
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";

const defaultButtons = [notetypeButtons, formatButtons, colorButtons, templateButtons];
const defaultMenus = [...templateMenus];

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;

    buttons = defaultButtons;
    menus = defaultMenus;
    disabled? = writable(false);

    connectedCallback(): void {
        setupI18n({ modules: [ModuleName.EDITING] }).then(() => {
            this.component = new EditorToolbarSvelte({
                target: this,
                props: {
                    buttons: this.buttons,
                    menus: this.menus,
                    disabled: this.disabled,
                    nightMode: checkNightMode(),
                },
            });
        });
    }

    update(): void {
        this.component?.$set({
            button: this.buttons,
            menus: this.menus,
        });
    }

    enableButtons(): void {
        this.disabled?.set(false);
    }

    disableButtons(): void {
        this.disabled?.set(true);
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
export { default as WithDropdownMenu } from "./WithDropdownMenu.svelte";
