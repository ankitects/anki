<!--
    Copyright: Ankitects Pty Ltd and contributors
    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    let forId: string;
    export { forId as for };
    export let preventMouseClick = false;

    const dispatch = createEventDispatcher();

    let spanRef: HTMLSpanElement;

    onMount(() => {
        dispatch("mount", { span: spanRef });
    });
</script>

<!-- svelte-ignore a11y-no-noninteractive-element-interactions -->
<!-- svelte-ignore a11y-click-events-have-key-events -->
<label
    bind:this={spanRef}
    for={forId}
    on:click={(e) => {
        if (preventMouseClick) {
            e.preventDefault();
        }
    }}
>
    <slot />
</label>

<style lang="scss">
    label {
        display: inline;
    }
</style>
