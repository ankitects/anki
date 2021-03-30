import type { SvelteComponent } from "svelte";

import { checkNightMode } from "anki/nightmode";
import { setupI18n, ModuleName } from "anki/i18n";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import LabelButton from "./LabelButton.svelte";
import DropdownMenu from "./DropdownMenu.svelte";

// @ts-ignore
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
import { Writable, writable } from "svelte/store";

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

const defaultButtons = [
    [
        { component: LabelButton, label: "Fields..." },
        { component: LabelButton, label: "Cards..." },
    ],
    [
        boldButton,
        italicButton,
        underlineButton,
        superscriptButton,
        subscriptButton,
        eraserButton,
    ],
    [forecolorButton, colorpickerButton],
    [attachmentButton, recordButton, clozeButton, mathjaxButton, htmlButton],
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
