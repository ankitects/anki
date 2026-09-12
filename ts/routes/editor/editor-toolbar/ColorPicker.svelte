<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Shortcut from "$lib/components/Shortcut.svelte";
    import { saveCustomColours } from "@generated/backend";

    export let keyCombination: string | null = null;
    export let value: string;

    let inputRef: HTMLInputElement;
</script>

<input
    bind:this={inputRef}
    tabindex="-1"
    type="color"
    bind:value
    on:input
    on:change
    on:click={() => saveCustomColours({})}
/>

{#if keyCombination}
    <Shortcut {keyCombination} on:action={() => inputRef.click()} />
{/if}

<style lang="scss">
    input {
        display: inline-block;
        width: 100%;
        height: 100%;
        cursor: pointer;
        opacity: 0;
    }
</style>
