// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { SvelteComponentDev } from "svelte/internal";
import type { ToolbarItem } from "./types";

import ButtonGroup from "./ButtonGroup.svelte";
import type { ButtonGroupProps } from "./ButtonGroup";

import { dynamicComponent } from "sveltelib/dynamicComponent";
import { Writable, writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import "./bootstrap.css";

import { Identifiable, search, add, insert } from "./identifiable";

interface Hideable {
    hidden?: boolean;
}

function showComponent(component: Hideable): void {
    component.hidden = false;
}

function hideComponent(component: Hideable): void {
    component.hidden = true;
}

function toggleComponent(component: Hideable): void {
    component.hidden = !component.hidden;
}

const buttonGroup = dynamicComponent<typeof ButtonGroup, ButtonGroupProps>(ButtonGroup);

let buttonsResolve: (
    value: Writable<(ToolbarItem<typeof ButtonGroup> & ButtonGroupProps)[]>
) => void;
let menusResolve: (value: Writable<ToolbarItem[]>) => void;

export class EditorToolbar extends HTMLElement {
    component?: SvelteComponentDev;

    buttonsPromise: Promise<
        Writable<(ToolbarItem<typeof ButtonGroup> & ButtonGroupProps)[]>
    > = new Promise((resolve) => {
        buttonsResolve = resolve;
    });
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

    updateButtonGroup<T>(
        update: (
            component: ToolbarItem<typeof ButtonGroup> & ButtonGroupProps & T
        ) => void,
        group: string | number
    ): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                const foundGroup = search(buttonGroups, group);

                if (foundGroup) {
                    update(
                        foundGroup as ToolbarItem<typeof ButtonGroup> &
                            ButtonGroupProps &
                            T
                    );
                }

                return buttonGroups;
            });

            return buttons;
        });
    }

    showButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(showComponent, group);
    }

    hideButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(hideComponent, group);
    }

    toggleButtonGroup(group: string | number): void {
        this.updateButtonGroup<Hideable>(toggleComponent, group);
    }

    insertButtonGroup(newGroup: ButtonGroupProps, group: string | number = 0): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                const newButtonGroup = buttonGroup(newGroup);
                return insert(buttonGroups, newButtonGroup, group);
            });

            return buttons;
        });
    }

    addButtonGroup(newGroup: ButtonGroupProps, group: string | number = -1): void {
        this.buttonsPromise.then((buttons) => {
            buttons.update((buttonGroups) => {
                const newButtonGroup = buttonGroup(newGroup);
                return add(buttonGroups, newButtonGroup, group);
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
            const foundButton = search(foundGroup.buttons, button);

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
        newButton: ToolbarItem & Identifiable,
        group: string | number,
        button: string | number = 0
    ): void {
        this.updateButtonGroup((component) => {
            component.buttons = insert(
                component.buttons as (ToolbarItem & Identifiable)[],
                newButton,
                button
            );
        }, group);
    }

    addButton(
        newButton: ToolbarItem & Identifiable,
        group: string | number,
        button: string | number = -1
    ): void {
        this.updateButtonGroup((component) => {
            component.buttons = add(
                component.buttons as (ToolbarItem & Identifiable)[],
                newButton,
                button
            );
        }, group);
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
