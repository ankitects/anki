<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    export let tooltip: string | undefined = undefined;

    let background: HTMLDivElement;
    const dispatch = createEventDispatcher();

    onMount(() => dispatch("mount", { background }));
</script>

<div
    bind:this={background}
    title={tooltip}
    on:mousedown|preventDefault
    on:click|stopPropagation
    on:dblclick
/>

<style lang="scss">
    div {
        width: 100%;
        height: 100%;
        background-color: black;
        opacity: 0.2;
    }
</style>
