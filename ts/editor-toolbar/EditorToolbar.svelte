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
    import type { Readable } from "svelte/store";
    import type { ToolbarItem, IterableToolbarItem } from "./types";
    import { setContext } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";

    import ButtonGroup from "./ButtonGroup.svelte";
    import type { ButtonGroupProps } from "./ButtonGroup";

    export let buttons: Readable<IterableToolbarItem>[];
    export let menus: Readable<ToolbarItem[]>;

    $: _buttons = $buttons;
    $: _menus = $menus;

    export let nightMode: boolean;

    setContext(nightModeKey, nightMode);
    setContext(disabledKey, disabled);

    export let size: number = 30;
    export let wraps: boolean = true;

    $: style = `--toolbar-size: ${size}px; --toolbar-wrap: ${
        wraps ? "wrap" : "nowrap"
    }`;
</script>

<style lang="scss">
    nav {
        position: sticky;
        top: 0;
        left: 0;
        z-index: 10;

        background: var(--bg-color);
        border-bottom: 1px solid var(--border);

        /* Remove outermost marigns */
        & > :global(ul) {
            & > :global(li:nth-child(1)) {
                margin-left: 0;
            }

            & > :global(li:nth-last-child(1)) {
                margin-right: 0;
            }
        }
    }
</style>

<div {style}>
    {#each _menus as menu}
        <svelte:component this={menu.component} {...menu} />
    {/each}
</div>

<nav {style}>
    <ButtonGroup items={_buttons} className="mt-0" />
</nav>
