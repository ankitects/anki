<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { revertIcon } from "./icons";
    import { createEventDispatcher } from "svelte";

    export let value: any;
    export let defaultValue: any;

    const dispatch = createEventDispatcher();

    let modified: boolean;
    $: modified = JSON.stringify(value) !== JSON.stringify(defaultValue);

    /// This component can be used either with bind:value, or by listening
    /// to the revert event.
    function revert(): void {
        value = JSON.parse(JSON.stringify(defaultValue));
        dispatch("revert", { value });
    }
</script>

<style>
    :global(svg) {
        vertical-align: text-bottom;
    }
</style>

{#if modified}
    <span on:click={revert}>{@html revertIcon}</span>
{/if}
