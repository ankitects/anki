<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    import IconConstrain from "./IconConstrain.svelte";

    let className = "";
    export { className as class };
    export let tooltip: string | undefined = undefined;

    export let iconSize = 100;
    export let widthMultiplier = 1;
    export let flipX = false;

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<button
    bind:this={spanRef}
    class="clickable-badge {className}"
    aria-label={tooltip}
    on:click
>
    <IconConstrain {iconSize} {widthMultiplier} {flipX}>
        <slot />
    </IconConstrain>
</button>

<style>
    .clickable-badge {
        color: var(--badge-color, inherit);
        background-color: transparent;
        border: none;
    }

    .dropdown-toggle::after {
        display: none;
    }
</style>
