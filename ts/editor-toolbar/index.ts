import type { SvelteComponent } from "svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

// @ts-ignore
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
import { writable } from "svelte/store";

import { notetypeButtons } from "./notetype";
import { formatButtons } from "./format";
import { colorButtons } from "./color";
import { templateButtons, templateMenus } from "./template";

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
