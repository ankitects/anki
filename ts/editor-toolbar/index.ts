// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { ToolbarItem, IterableToolbarItem } from "./types";
import type { Identifier } from "./identifiable";

import { writable } from "svelte/store";

import EditorToolbarSvelte from "./EditorToolbar.svelte";

import "./bootstrap.css";

import { add, insert, updateRecursive } from "./identifiable";
import { showComponent, hideComponent, toggleComponent } from "./hideable";

export interface EditorToolbarAPI {
    // Button API
    updateButton(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void;
    showButton(...identifiers: Identifier[]): void;
    hideButton(...identifiers: Identifier[]): void;
    toggleButton(...identifiers: Identifier[]): void;
    insertButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void;
    addButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void;

    // Menu API
    updateMenu(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void;
    addMenu(newMenu: ToolbarItem, ...identifiers: Identifier[]): void;
}

export function editorToolbar(
    target: HTMLElement,
    initialButtons: IterableToolbarItem[] = [],
    initialMenus: ToolbarItem[] = []
): EditorToolbarAPI {
    const buttons = writable(initialButtons);
    const menus = writable(initialMenus);

    new EditorToolbarSvelte({
        target,
        props: {
            buttons,
            menus,
            nightMode: document.documentElement.classList.contains("night-mode"),
        },
    });

    function updateButton(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        buttons.update(
            (items: IterableToolbarItem[]): IterableToolbarItem[] =>
                updateRecursive(
                    update,
                    ({ items } as unknown) as ToolbarItem,
                    ...identifiers
                ).items as IterableToolbarItem[]
        );
    }

    function showButton(...identifiers: Identifier[]): void {
        updateButton(showComponent, ...identifiers);
    }

    function hideButton(...identifiers: Identifier[]): void {
        updateButton(hideComponent, ...identifiers);
    }

    function toggleButton(...identifiers: Identifier[]): void {
        updateButton(toggleComponent, ...identifiers);
    }

    function insertButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateButton(
            (component: ToolbarItem) =>
                insert(component as IterableToolbarItem, newButton, lastIdentifier),

            ...initIdentifiers
        );
    }

    function addButton(newButton: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateButton(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newButton, lastIdentifier),
            ...initIdentifiers
        );
    }

    function updateMenu(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        menus.update(
            (items: ToolbarItem[]): ToolbarItem[] =>
                updateRecursive(
                    update,
                    ({ items } as unknown) as ToolbarItem,
                    ...identifiers
                ).items as ToolbarItem[]
        );
    }

    function addMenu(newMenu: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateMenu(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newMenu, lastIdentifier),
            ...initIdentifiers
        );
    }

    return {
        updateButton,
        showButton,
        hideButton,
        toggleButton,
        insertButton,
        addButton,
        updateMenu,
        addMenu,
    };
}

/* Exports for editor */
// @ts-expect-error insufficient typing of svelte modules
export { updateActiveButtons, clearActiveButtons } from "./CommandIconButton.svelte";
// @ts-expect-error insufficient typing of svelte modules
export { enableButtons, disableButtons } from "./EditorToolbar.svelte";
