<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    let className = "";
    export { className as class };

    const dispatch = createEventDispatcher();
    let element: HTMLElement;

    onMount(async () => {
        dispatch("mount", { element });
    });
</script>

<td bind:this={element} class={className}>
    <slot />
</td>
