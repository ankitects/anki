<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    export let container: HTMLElement;
    export let activeImage: HTMLImageElement | null;

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
    style="--left: {left}px; --top: {top}px; --width: {width}px; --height: {height}px;"
>
    <slot />
</div>

<style lang="scss">
    div {
        position: absolute;

        left: var(--left, 0);
        top: var(--top, 0);
        width: var(--width);
        height: var(--height);
    }
</style>
