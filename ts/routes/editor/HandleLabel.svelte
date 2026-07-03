<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { directionKey } from "@tslib/context-keys";
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
</script>

<div class="handle-label" class:is-rtl={$direction === "rtl"}>
    <slot />
</div>

<style lang="scss">
    .handle-label {
        position: absolute;
        width: fit-content;

        left: 0;
        right: 0;
        bottom: 3px;

        margin-left: auto;
        margin-right: auto;

        font-size: 13px;
        color: white;
        background-color: rgba(0 0 0 / 0.4);
        border-color: black;
        border-radius: 5px;
        padding: 0 5px;

        pointer-events: none;
        user-select: none;
    }
</style>
