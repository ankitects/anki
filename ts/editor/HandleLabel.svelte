<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { afterUpdate, createEventDispatcher, onMount } from "svelte";

    export let isRtl: boolean;

    let dimensions: HTMLDivElement;
    let overflowFix = 0;

    afterUpdate(() => {
        const boundingClientRect = dimensions.getBoundingClientRect();
        const overflow = isRtl
            ? window.innerWidth - boundingClientRect.x - boundingClientRect.width
            : boundingClientRect.x;

        overflowFix = Math.min(0, overflowFix + overflow, overflow);
    });

    const dispatch = createEventDispatcher();

    onMount(() => dispatch("mount"));
</script>

<div
    bind:this={dimensions}
    class="image-handle-dimensions"
    class:is-rtl={isRtl}
    style="--overflow-fix: {overflowFix}px"
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
        border-radius: 0.25rem;
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
