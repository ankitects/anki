<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let container: HTMLElement;
    export let activeImage: HTMLImageElement | null;

    export let offsetX = 0;
    export let offsetY = 0;

    $: if (activeImage) {
        updateSelection();
    } else {
        reset();
    }

    let left: number;
    let top: number;
    let width: number;
    let height: number;

    export function updateSelection() {
        const containerRect = container.getBoundingClientRect();
        const imageRect = activeImage!.getBoundingClientRect();

        const containerLeft = containerRect.left;
        const containerTop = containerRect.top;

        left = imageRect!.left - containerLeft;
        top = imageRect!.top - containerTop;
        width = activeImage!.clientWidth;
        height = activeImage!.clientHeight;
    }

    function reset() {
        activeImage = null;

        left = 0;
        top = 0;
        width = 0;
        height = 0;
    }
</script>

<div
    style="--left: {left}px; --top: {top}px; --width: {width}px; --height: {height}px; --offsetX: {offsetX}px; --offsetY: {offsetY}px;"
>
    <slot />
</div>

<style lang="scss">
    div {
        position: absolute;

        left: calc(var(--left, 0px) - var(--offsetX, 0px));
        top: calc(var(--top, 0px) - var(--offsetY, 0px));
        width: calc(var(--width) + 2 * var(--offsetX, 0px));
        height: calc(var(--height) + 2 * var(--offsetY, 0px));
    }
</style>
