<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let html = "";
    export let x: number = 0;
    export let y: number = 0;
    export let show = true;

    let container = null as unknown as HTMLDivElement;

    let adjustedX: number, adjustedY: number;

    let shiftLeftAmount = 0;
    $: shiftLeftAmount = container
        ? Math.round(container.clientWidth * 1.2 * (x / document.body.clientWidth))
        : 0;

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
        padding: 15px;
        border-radius: 5px;
        font-size: 15px;
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.3s;
        color: var(--text-fg);
        background: var(--tooltip-bg);

        :global(table) {
            border-spacing: 1em 0;
        }
    }
</style>
