<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { Placement } from "@floating-ui/dom";
    import { createEventDispatcher, getContext, onMount } from "svelte";
    import type { Writable } from "svelte/store";

    import { floatingKey } from "./context-keys";

    export let id = "";
    export let scrollable = false;
    let wrapper: HTMLDivElement;
    let hidden = true;
    let minHeight = 0;

    let placement: Placement;

    const dispatch = createEventDispatcher();

    const placementStore = getContext<Writable<Promise<Placement>>>(floatingKey);

    /* await computed placement of floating element to determine animation direction */
    $: if ($placementStore !== undefined && hidden) {
        $placementStore.then((computedPlacement) => {
            if (placement != computedPlacement) {
                placement = computedPlacement;
                hidden = false;
            }
        });
    }

    onMount(async () => {
        /* set min-height on wrapper to ensure correct
           popover placement at animation start */
        minHeight = wrapper.offsetHeight;
    });
    function revealed(el: HTMLElement) {
        dispatch("revealed", el);
    }
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
        use:revealed
        {id}
        role="listbox"
    >
        <slot />
    </div>
</div>

<style lang="scss">
    @use "../sass/elevation" as elevation;

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
            max-height: 200px;
            overflow: hidden auto;
        }

        &.hidden {
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
