<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher } from "svelte";

    import DropdownItem from "./DropdownItem.svelte";

    export let disabled = false;

    const dispatch = createEventDispatcher();
    let element: HTMLButtonElement;

    function onSelect(): void {
        if (!disabled) {
            dispatch("select");
        }
    }
</script>

<DropdownItem {disabled} on:click={onSelect} bind:buttonRef={element}>
    <slot />
</DropdownItem>
