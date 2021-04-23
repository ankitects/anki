// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponentDev } from "svelte/internal";
import type { ToolbarItem, IterableToolbarItem } from "./types";

import { Writable, writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import "./bootstrap.css";

import { search, add, insert } from "./identifiable";
import { showComponent, hideComponent, toggleComponent } from "./hideable";

let buttonsResolve: (value: Writable<IterableToolbarItem[]>) => void;
let menusResolve: (value: Writable<ToolbarItem[]>) => void;

export class EditorToolbar extends HTMLElement {
    component?: SvelteComponentDev;

    buttonsPromise: Promise<Writable<IterableToolbarItem[]>> = new Promise(
        (resolve) => {
            buttonsResolve = resolve;
        }
    );
    menusPromise: Promise<Writable<ToolbarItem[]>> = new Promise((resolve): void => {
        menusResolve = resolve;
    });

    connectedCallback(): void {
        globalThis.$editorToolbar = this;

        const buttons = writable([]);
        const menus = writable([]);

        this.component = new EditorToolbarSvelte({
            target: this,
            props: {
                buttons,
                menus,
                nightMode: document.documentElement.classList.contains("night-mode"),
            },
        });

        buttonsResolve(buttons);
        menusResolve(menus);
    }

    updateButtonGroup(
        update: (component: IterableToolbarItem) => void,
        group: string | number
    ): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                const foundGroup = search(buttonGroups, group);

                if (foundGroup) {
                    update(foundGroup as IterableToolbarItem);
                }

                return buttonGroups;
            });

            return buttons;
        });
    }

    showButtonGroup(group: string | number): void {
        this.updateButtonGroup(showComponent, group);
    }

    hideButtonGroup(group: string | number): void {
        this.updateButtonGroup(hideComponent, group);
    }

    toggleButtonGroup(group: string | number): void {
        this.updateButtonGroup(toggleComponent, group);
    }

    insertButtonGroup(newGroup: IterableToolbarItem, group: string | number = 0): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                return insert(buttonGroups, newGroup, group);
            });

            return buttons;
        });
    }

    addButtonGroup(newGroup: IterableToolbarItem, group: string | number = -1): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                return add(buttonGroups, newGroup, group);
            });

            return buttons;
        });
    }

    updateButton(
        update: (component: ToolbarItem) => void,
        group: string | number,
        button: string | number
    ): void {
        this.updateButtonGroup((foundGroup) => {
            const foundButton = search(foundGroup.items, button);

            if (foundButton) {
                update(foundButton);
            }
        }, group);
    }

    showButton(group: string | number, button: string | number): void {
        this.updateButton(showComponent, group, button);
    }

    hideButton(group: string | number, button: string | number): void {
        this.updateButton(hideComponent, group, button);
    }

    toggleButton(group: string | number, button: string | number): void {
        this.updateButton(toggleComponent, group, button);
    }

    insertButton(
        newButton: ToolbarItem,
        group: string | number,
        button: string | number = 0
    ): void {
        this.updateButtonGroup((component: IterableToolbarItem) => {
            component.items = insert(component.items, newButton, button);
        }, group);
    }

    addButton(
        newButton: ToolbarItem,
        group: string | number,
        button: string | number = -1
    ): void {
        this.updateButtonGroup((component: IterableToolbarItem) => {
            component.items = add(component.items, newButton, button);
        }, group);
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
