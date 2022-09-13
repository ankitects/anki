<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { cubicOut } from "svelte/easing";
    import { tweened } from "svelte/motion";

    import { removeStyleProperties } from "../lib/styling";

    export let duration = 300;

    export let collapse = false;
    let collapsed = false;

    const size = tweened<number>(undefined, {
        duration,
        easing: cubicOut,
    });

    function doCollapse(collapse: boolean): void {
        if (collapse) {
            size.set(0);
        } else {
            collapsed = false;
            size.set(1, { duration: 0 });
        }
    }

    $: doCollapse(collapse);

    let collapsibleElement: HTMLElement;
    let clientHeight: number;

    function updateHeight(percentage: number): void {
        collapsibleElement.style.overflow = "hidden";

        if (percentage === 1) {
            removeStyleProperties(collapsibleElement, "height", "overflow");
        } else if (percentage === 0) {
            collapsed = true;
            removeStyleProperties(collapsibleElement, "height", "overflow");
        } else {
            collapsibleElement.style.height = `${percentage * clientHeight}px`;
        }
    }

    $: if (collapsibleElement) {
        updateHeight($size);
    }
</script>

{#if !collapsed}
    <div bind:this={collapsibleElement} class="collapsible" bind:clientHeight>
        <slot />
    </div>
{/if}
