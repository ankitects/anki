<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";
    import { cubicIn, cubicOut } from "svelte/easing";
    import { tweened } from "svelte/motion";

    export let collapse = false;
    export let toggleDisplay = false;
    export let animated = !document.body.classList.contains("reduce-motion");

    let contentHeight = 0;

    function dynamicDuration(height: number): number {
        return 100 + Math.pow(height, 1 / 4) * 25;
    }
    $: duration = dynamicDuration(contentHeight);

    const size = tweened<number | undefined>(undefined);

    async function transition(collapse: boolean): Promise<void> {
        if (collapse) {
            contentHeight = collapsibleElement.clientHeight;
            size.set(0, {
                duration: duration,
                easing: cubicOut,
            });
        } else {
            /* Tell content to show and await response */
            collapsed = false;
            await tick();
            /* Measure content height to tween to */
            contentHeight = collapsibleElement.clientHeight;
            size.set(1, {
                duration: duration,
                easing: cubicIn,
            });
        }
    }

    $: if (collapsibleElement) {
        if (animated) {
            transition(collapse);
        } else {
            collapsed = collapse;
        }
    }

    let collapsibleElement: HTMLElement;

    $: collapsed = ($size ?? 0) === 0;
    $: expanded = $size === 1;
    $: height = ($size ?? 0) * contentHeight;
    $: transitioning = ($size ?? 0) > 0 && !(collapsed || expanded);
    $: measuring = !(collapsed || transitioning || expanded);

    let hidden = collapsed;

    $: {
        /* await changes dependent on collapsed state */
        tick().then(() => (hidden = collapsed));
    }
</script>

<div
    bind:this={collapsibleElement}
    class="collapsible"
    class:animated
    class:expanded
    class:full-hide={toggleDisplay}
    class:measuring
    class:transitioning
    class:hidden
    style:--height="{height}px"
>
    <slot {collapsed} />
</div>

{#if animated && measuring}
    <!-- Maintain document flow while collapsible height is measured -->
    <div class="collapsible-placeholder"></div>
{/if}

<style lang="scss">
    .collapsible {
        &.animated {
            &.measuring {
                display: initial;
                position: absolute;
                opacity: 0;
            }

            &.transitioning {
                overflow: hidden;
                height: var(--height);
                &.expanded {
                    overflow: visible;
                }
                &.full-hide {
                    display: initial;
                }
            }
        }
        &.full-hide {
            &.hidden {
                display: none;
            }
        }
    }
</style>
