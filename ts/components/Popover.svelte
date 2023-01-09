<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Placement } from "@floating-ui/dom";
    import { getContext, onMount } from "svelte";
    import { create_in_transition } from "svelte/internal";
    import type { Writable } from "svelte/store";
    import { slide } from "svelte/transition";

    import { floatingKey } from "./context-keys";

    export let scrollable = false;
    let element: HTMLDivElement;
    let wrapper: HTMLDivElement;
    let hidden = true;
    let minHeight = 0;

    let placement: Placement;

    const placementStore = getContext<Writable<Promise<Placement>>>(floatingKey);

    /* await computed placement of floating element to determine animation direction */
    $: if ($placementStore !== undefined && hidden) {
        $placementStore.then((computedPlacement) => {
            if (placement != computedPlacement) {
                placement = computedPlacement;
                /* use internal function to animate popover */
                create_in_transition(element, slide, { duration: 200 }).start();
                hidden = false;
            }
        });
    }

    onMount(async () => {
        /* set min-height on wrapper to ensure correct
           popover placement at animation start */
        minHeight = wrapper.offsetHeight;
    });
</script>

<div
    class="popover-wrapper d-flex"
    style:--min-height="{minHeight}px"
    bind:this={wrapper}
>
    <div
        class="popover"
        class:scrollable
        class:hidden
        class:top={placement === "top"}
        class:right={placement === "right"}
        class:bottom={placement === "bottom"}
        class:left={placement === "left"}
        bind:this={element}
    >
        <slot />
    </div>
</div>

<style lang="scss">
    @use "sass/elevation" as elevation;

    .popover-wrapper {
        min-height: var(--min-height, 0);
    }

    .popover {
        @include elevation.elevation(8);

        align-self: flex-start;
        border-radius: var(--border-radius);
        background-color: var(--canvas-elevated);
        border: 1px solid var(--border-subtle);

        min-width: var(--popover-width, 1rem);
        max-width: 95vw;

        /* Needs this much space for FloatingArrow to be positioned */
        padding: var(--popover-padding-block, 6px) var(--popover-padding-inline, 6px);

        &.scrollable {
            max-height: 400px;
            overflow: hidden auto;
        }

        &.hidden {
            width: 0;
            visibility: hidden;
        }

        /* alignment determines slide animation direction */
        &.top,
        &.left {
            align-self: flex-end;
        }
        &.bottom,
        &.right {
            align-self: flex-start;
        }
    }
</style>
