<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { cubicIn, cubicOut } from "svelte/easing";
    import { tweened } from "svelte/motion";

    export let duration = 200;
    export let animated = true;

    function dynamicDuration(height: number, factor: number): number {
        return 100 + Math.pow(height, 1 / 4) * factor;
    }

    export let collapse = false;
    let collapsed = false;
    let expandHeight: number;

    const size = tweened<number>(undefined);

    async function doCollapse(collapse: boolean): Promise<void> {
        if (collapse) {
            expandHeight = collapsibleElement.clientHeight;
            size.set(0, {
                duration: duration || dynamicDuration(expandHeight, 25),
                easing: cubicOut,
            });
        } else {
            collapsed = false;

            /* Measure height to tween to */
            await new Promise(requestAnimationFrame);
            await new Promise(requestAnimationFrame);
            expandHeight = collapsibleElement.clientHeight;

            transitioning = true;
            size.set(1, {
                duration: duration || dynamicDuration(expandHeight, 25),
                easing: cubicIn,
            });
        }
    }

    $: if (collapsibleElement) {
        if (animated) {
            doCollapse(collapse);
        } else {
            collapsed = collapse;
        }
    }

    let collapsibleElement: HTMLElement;

    $: collapsed = $size === 0;
    $: expanded = $size === 1;
    $: transitioning = $size > 0 && !(collapsed || expanded);

    $: height = $size * expandHeight;
    $: measuring = !(collapsed || transitioning || expanded);
</script>

<div
    bind:this={collapsibleElement}
    class="collapsible"
    class:measuring
    class:animated
    class:transitioning
    class:expanded
    style:--height="{height}px"
>
    <slot {collapsed} />
</div>

{#if measuring}
    <!-- Placeholder while element is absolutely positioned during measurement -->
    <div class="dummy" />
{/if}

<style lang="scss">
    .collapsible.animated {
        &.measuring {
            position: absolute;
            opacity: 0;
        }
        &.transitioning {
            overflow: hidden;
            height: var(--height);
            &.expanded {
                overflow: visible;
            }
        }
    }
</style>
