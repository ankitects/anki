import type { SvelteComponent } from "svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import DropdownMenu from "./DropdownMenu.svelte";
import WithDropdownMenu from "./WithDropdownMenu.svelte";

// @ts-ignore
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
import { Writable, writable } from "svelte/store";

import { fieldsButton, cardsButton } from "./notetype";

import {
    boldButton,
    italicButton,
    underlineButton,
    superscriptButton,
    subscriptButton,
    eraserButton,
} from "./format";

import { forecolorButton, colorpickerButton } from "./color";

import {
    attachmentButton,
    recordButton,
    clozeButton,
    mathjaxButton,
    htmlButton,
} from "./extra";

const defaultMenus = [
    {
        component: DropdownMenu,
        id: "mathjaxMenu",
        menuItems: [{ label: "Foo", onClick: () => console.log("foo") }],
    },
];

const defaultButtons = [
    [fieldsButton, cardsButton],
    [
        boldButton,
        italicButton,
        underlineButton,
        superscriptButton,
        subscriptButton,
        eraserButton,
    ],
    [forecolorButton, colorpickerButton],
    [
        attachmentButton,
        recordButton,
        clozeButton,
        { component: WithDropdownMenu, menuId: "mathjaxMenu", button: mathjaxButton },
        htmlButton,
    ],
];

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;
    disabled?: Writable<boolean>;

    connectedCallback(): void {
        this.disabled = writable(false);

        setupI18n({ modules: [ModuleName.STATISTICS, ModuleName.SCHEDULING] }).then(
            () => {
                this.component = new EditorToolbarSvelte({
                    target: this,
                    props: {
                        menus: defaultMenus,
                        buttons: defaultButtons,
                        nightMode: checkNightMode(),
                        disabled: this.disabled,
                    },
                });
            }
        );
    }

    enableButtons(): void {
        this.disabled?.set(false);
    }

    disableButtons(): void {
        this.disabled?.set(true);
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);
