<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Readable } from "svelte/store";
    import { getContext } from "svelte";
    import { directionKey } from "../lib/context-keys";
    import { afterUpdate, createEventDispatcher, onMount } from "svelte";

    let dimensions: HTMLDivElement;
    let overflowFix = 0;

    const direction = getContext<Readable<"ltr" | "rtl">>(directionKey);

    function updateOverflow(dimensions: HTMLDivElement): void {
        const boundingClientRect = dimensions.getBoundingClientRect();
        const overflow =
            $direction === "ltr"
                ? boundingClientRect.x
                : window.innerWidth - boundingClientRect.x - boundingClientRect.width;

        overflowFix = Math.min(0, overflowFix + overflow, overflow);
    }

    afterUpdate(() => updateOverflow(dimensions));

    function updateOverflowAsync(dimensions: HTMLDivElement): void {
        setTimeout(() => updateOverflow(dimensions));
    }

    const dispatch = createEventDispatcher();

    onMount(() => dispatch("mount"));
</script>

<div
    bind:this={dimensions}
    class="image-handle-dimensions"
    class:is-rtl={$direction === "rtl"}
    style="--overflow-fix: {overflowFix}px"
    use:updateOverflowAsync
>
    <slot />
</div>

<style lang="scss">
    div {
        position: absolute;

        pointer-events: none;
        user-select: none;

        font-size: 13px;
        color: white;
        background-color: rgba(0 0 0 / 0.3);
        border-color: black;
        border-radius: 5px;
        padding: 0 5px;

        bottom: 3px;
        right: 3px;
        margin-left: 3px;
        margin-right: var(--overflow-fix, 0);

        &.is-rtl {
            right: auto;
            left: 3px;
            margin-right: 3px;
            margin-left: var(--overflow-fix, 0);
        }
    }
</style>
