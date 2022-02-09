<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getContext } from "svelte";
    import type { Readable } from "svelte/store";

    import { directionKey } from "../lib/context-keys";

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);
</script>

<div
    class="label-container"
    class:rtl={$direction === "rtl"}
    on:mousedown|preventDefault
>
    <slot />
</div>

<style lang="scss">
    .label-container {
        display: flex;
        justify-content: space-between;

        background-color: var(--label-color, transparent);

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
