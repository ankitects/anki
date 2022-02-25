<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { afterUpdate, createEventDispatcher, onMount } from "svelte";

    import { directionProperty } from "../sveltelib/context-property";

    let dimensions: HTMLDivElement;
    let overflowFix = 0;

    const direction = directionProperty.get();

    function updateOverflow(dimensions: HTMLDivElement): void {
        const boundingClientRect = dimensions.getBoundingClientRect();
        const overflow =
            $direction === "ltr"
                ? boundingClientRect.x
                : window.innerWidth - boundingClientRect.x - boundingClientRect.width;

        overflowFix = Math.min(0, overflowFix + overflow, overflow);
    }

    afterUpdate(() => updateOverflow(dimensions));

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
