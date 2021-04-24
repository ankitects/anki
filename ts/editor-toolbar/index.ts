// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem, IterableToolbarItem } from "./types";
import type { Identifier } from "./identifiable";

import { Writable, writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import "./bootstrap.css";

import { add, insert, updateRecursive } from "./identifiable";
import { showComponent, hideComponent, toggleComponent } from "./hideable";

let buttonsResolve: (value: Writable<IterableToolbarItem[]>) => void;
let menusResolve: (value: Writable<ToolbarItem[]>) => void;

export class EditorToolbar extends HTMLElement {
    private buttonsPromise: Promise<Writable<IterableToolbarItem[]>> = new Promise(
        (resolve) => {
            buttonsResolve = resolve;
        }
    );
    private menusPromise: Promise<Writable<ToolbarItem[]>> = new Promise(
        (resolve): void => {
            menusResolve = resolve;
        }
    );

    connectedCallback(): void {
        globalThis.$editorToolbar = this;

        const buttons = writable([]);
        const menus = writable([]);

        new EditorToolbarSvelte({
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

    updateButton(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        this.buttonsPromise.then(
            (
                buttons: Writable<IterableToolbarItem[]>
            ): Writable<IterableToolbarItem[]> => {
                buttons.update(
                    (items: IterableToolbarItem[]): IterableToolbarItem[] =>
                        updateRecursive(
                            update,
                            ({ items } as unknown) as ToolbarItem,
                            ...identifiers
                        ).items as IterableToolbarItem[]
                );

                return buttons;
            }
        );
    }

    showButton(...identifiers: Identifier[]): void {
        this.updateButton(showComponent, ...identifiers);
    }

    hideButton(...identifiers: Identifier[]): void {
        this.updateButton(hideComponent, ...identifiers);
    }

    toggleButton(...identifiers: Identifier[]): void {
        this.updateButton(toggleComponent, ...identifiers);
    }

    insertButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        this.updateButton(
            (component: ToolbarItem) =>
                insert(component as IterableToolbarItem, newButton, lastIdentifier),

            ...initIdentifiers
        );
    }

    addButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        this.updateButton(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newButton, lastIdentifier),
            ...initIdentifiers
        );
    }

    updateMenu(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        this.menusPromise.then(
            (menus: Writable<ToolbarItem[]>): Writable<ToolbarItem[]> => {
                menus.update(
                    (items: ToolbarItem[]): ToolbarItem[] =>
                        updateRecursive(
                            update,
                            ({ items } as unknown) as ToolbarItem,
                            ...identifiers
                        ).items as ToolbarItem[]
                );

                return menus;
            }
        );
    }

    addMenu(newMenu: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        this.updateMenu(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newMenu, lastIdentifier),
            ...initIdentifiers
        );
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
