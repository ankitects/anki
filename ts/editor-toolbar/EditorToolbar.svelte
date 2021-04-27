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

    import { setContext, onMount } from "svelte";
    import { disabledKey, nightModeKey } from "./contextKeys";
    import { add, insert, updateRecursive } from "./identifiable";
    import { showComponent, hideComponent, toggleComponent } from "./hideable";

    import NoteTypeButtons from "./NoteTypeButtons.svelte";

    let api = {};

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

        background-color: var(--window-bg);
        border-bottom: 1px solid var(--border);
    }
</style>

<nav {style} class="pb-1">
    <NoteTypeButtons />
</nav>
