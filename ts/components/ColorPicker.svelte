<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import { onMount, createEventDispatcher } from "svelte";

    let inputRef: HTMLInputElement;

    const dispatch = createEventDispatcher();
    onMount(() => dispatch("mount", { input: inputRef }));
</script>

<input tabindex="-1" bind:this={inputRef} type="color" on:change />

<style lang="scss">
    input {
        display: inline-block;
        width: 100%;
        height: 100%;
        cursor: pointer;
        opacity: 0;
    }
</style>
