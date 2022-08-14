<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount } from "svelte";

    import type { Callback } from "../lib/typing";

    export let container: HTMLElement;
    export let image: HTMLImageElement;
    export let updater: (callback: Callback) => Callback;

    export let offsetX = 0;
    export let offsetY = 0;

    let left = 0;
    let top = 0;
    let width = 0;
    let height = 0;

    function setSelection(): void {
        const containerRect = container.getBoundingClientRect();
        const imageRect = image.getBoundingClientRect();

        const containerLeft = containerRect.left;
        const containerTop = containerRect.top;

        left = imageRect.left - containerLeft;
        top = imageRect.top - containerTop;

        width = image.clientWidth;
        height = image.clientHeight;
    }

    onMount(() => updater(setSelection))
</script>

<div
    class="handle-selection"
    use:setSelection
    style:--left="{left}px"
    style:--top="{top}px"
    style:--width="{width}px"
    style:--height="{height}px"
    style:--offsetX="{offsetX}px"
    style:--offsetY="{offsetY}px"
>
    <slot />
</div>

<style lang="scss">
    .handle-selection {
        position: absolute;
        z-index: -1;

        left: calc(var(--left) - var(--offsetX));
        top: calc(var(--top) - var(--offsetY));
        width: calc(var(--width) + 2 * var(--offsetX));
        height: calc(var(--height) + 2 * var(--offsetY));
    }
</style>
