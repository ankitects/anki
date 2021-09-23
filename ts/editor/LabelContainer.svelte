<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { EditingAreaAPI } from "./EditingArea.svelte";
    import type { Readable } from "svelte/store";
    import { getContext, getAllContexts } from "svelte";
    import { directionKey, editingAreaKey } from "lib/context-keys";

    const editingArea = getContext<EditingAreaAPI>(editingAreaKey);
    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
</script>

<div
    class="label-container d-flex justify-content-between"
    class:rtl={$direction === "rtl"}
    on:mousedown|preventDefault
    on:click={() => editingArea.focus()}
>
    <slot />
</div>

<style lang="scss">
    .label-container {
        border-width: 0 0 1px;
        border-style: dashed;
        border-color: var(--border-color);
        border-radius: 5px 5px 0 0;

        padding: 0px 6px;
    }

    .rtl {
        direction: rtl;
    }
</style>
