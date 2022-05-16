<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import { createEventDispatcher, onMount } from "svelte";
    import type { Readable } from "svelte/store";

    import { directionKey } from "../lib/context-keys";

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
    const dispatch = createEventDispatcher();

    onMount(() => dispatch("mount"));
</script>

<div class="image-handle-dimensions" class:is-rtl={$direction === "rtl"}>
    <slot />
</div>

<style lang="scss">
    div {
        position: absolute;
        width: fit-content;

        left: 0;
        right: 0;
        bottom: 3px;

        margin-left: auto;
        margin-right: auto;

        font-size: 13px;
        color: white;
        background-color: rgba(0 0 0 / 0.3);
        border-color: black;
        border-radius: 5px;
        padding: 0 5px;

        pointer-events: none;
        user-select: none;
    }
</style>
