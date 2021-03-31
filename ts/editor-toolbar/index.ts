import type { SvelteComponent } from "svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName, i18n } from "anki/i18n";
import * as tr from "anki/i18n";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

// @ts-ignore
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
import { Writable, writable } from "svelte/store";

import { notetypeButtons } from "./notetype";
import { formatButtons } from "./format";
import { colorButtons } from "./color";
import { templateButtons, templateMenus } from "./template";

const defaultMenus = [...templateMenus];

const defaultButtons = [notetypeButtons, formatButtons, colorButtons, templateButtons];

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;
    disabled?: Writable<boolean>;

    connectedCallback(): void {
        this.disabled = writable(false);

        setupI18n({ modules: [ModuleName.EDITING] }).then(() => {
            console.log(i18n, tr);

            this.component = new EditorToolbarSvelte({
                target: this,
                props: {
                    menus: defaultMenus,
                    buttons: defaultButtons,
                    nightMode: checkNightMode(),
                    disabled: this.disabled,
                },
            });
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
