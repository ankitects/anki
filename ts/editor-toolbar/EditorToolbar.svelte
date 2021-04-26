<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="typescript">
    import "./legacy.css";
    import { writable } from "svelte/store";

    const disabled = writable(false);

    export function enableButtons(): void {
        disabled.set(false);
    }

    export function disableButtons(): void {
        disabled.set(true);
    }
</script>

<script lang="typescript">
    import type { Identifier } from "./identifiable";
    import type { ToolbarItem, IterableToolbarItem } from "./types";

    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "sveltelib/contextKeys";
    import { add, insert, updateRecursive } from "./identifiable";
    import { showComponent, hideComponent, toggleComponent } from "./hideable";

    import StickyBar from "sveltelib/StickyBar.svelte";
    import ButtonGroup from "sveltelib/ButtonGroup.svelte";

    export let buttons: IterableToolbarItem[];
    export let menus: ToolbarItem[];
    export let nightMode: boolean;

    setContext(nightModeKey, nightMode);
    setContext(disabledKey, disabled);

    export let size: number = 30;
    export let wraps: boolean = true;

    $: style = `--toolbar-size: ${size}px; --toolbar-wrap: ${
        wraps ? "wrap" : "nowrap"
    }`;

    export function updateButton(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        buttons = updateRecursive(
            update,
            ({ items: buttons } as unknown) as ToolbarItem,
            ...identifiers
        ).items as IterableToolbarItem[];
    }

    export function showButton(...identifiers: Identifier[]): void {
        updateButton(showComponent, ...identifiers);
    }

    export function hideButton(...identifiers: Identifier[]): void {
        updateButton(hideComponent, ...identifiers);
    }

    export function toggleButton(...identifiers: Identifier[]): void {
        updateButton(toggleComponent, ...identifiers);
    }

    export function insertButton(
        newButton: ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateButton(
            (component: ToolbarItem) =>
                insert(component as IterableToolbarItem, newButton, lastIdentifier),

            ...initIdentifiers
        );
    }

    export function addButton(
        newButton: ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateButton(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newButton, lastIdentifier),
            ...initIdentifiers
        );
    }

    export function updateMenu(
        update: (component: ToolbarItem) => ToolbarItem,
        ...identifiers: Identifier[]
    ): void {
        menus = updateRecursive(
            update,
            ({ items: menus } as unknown) as ToolbarItem,
            ...identifiers
        ).items as ToolbarItem[];
    }

    export function addMenu(newMenu: ToolbarItem, ...identifiers: Identifier[]): void {
        const initIdentifiers = identifiers.slice(0, -1);
        const lastIdentifier = identifiers[identifiers.length - 1];
        updateMenu(
            (component: ToolbarItem) =>
                add(component as IterableToolbarItem, newMenu, lastIdentifier),
            ...initIdentifiers
        );
    }
</script>

<div {style}>
    {#each menus as menu}
        <svelte:component this={menu.component} {...menu} />
    {/each}

    <StickyBar>
        <ButtonGroup items={buttons} className="p-0 mb-1" />
    </StickyBar>
</div>
