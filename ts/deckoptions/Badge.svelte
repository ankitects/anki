<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, createEventDispatcher } from "svelte";

    let className = "";
    export { className as class };

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<span bind:this={spanRef} class={`badge ${className}`} on:click>
    <slot />
</span>

<style>
    .badge {
        color: inherit;
    }

    span :global(svg) {
        vertical-align: -0.125rem;
    }
</style>
