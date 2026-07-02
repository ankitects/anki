<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";

    export let html = "";
    export let x: number = 0;
    export let y: number = 0;
    export let show = true;

    let container: HTMLDivElement | null = null;

    let adjustedX: number, adjustedY: number;

    let shiftLeftAmount = 0;
    $: onXChange(x);

    async function onXChange(xPos: number) {
        await tick();
        shiftLeftAmount = container
            ? Math.round(
                  container.clientWidth * 1.2 * (xPos / document.body.clientWidth),
              )
            : 0;
    }

    $: {
        // move tooltip away from edge as user approaches right side
        adjustedX = x + 40 - shiftLeftAmount;
        adjustedY = y + 40;
    }
</script>

<div
    bind:this={container}
    class="tooltip"
    style="left: {adjustedX}px; top: {adjustedY}px; opacity: {show ? 1 : 0}"
>
    {@html html}
</div>

<style lang="scss">
    .tooltip {
        position: absolute;
        white-space: nowrap;
        padding: 15px;
        border-radius: 5px;
        font-family: inherit;
        font-size: 15px;
        opacity: 0;
        pointer-events: none;
        transition: opacity var(--transition);
        color: var(--fg);
        background: var(--canvas-overlay);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

        :global(table) {
            border-spacing: 1em 0;
        }
    }
</style>
