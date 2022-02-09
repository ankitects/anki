<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { createEventDispatcher, onMount } from "svelte";

    export let container: HTMLElement;
    export let image: HTMLImageElement;

    export let offsetX = 0;
    export let offsetY = 0;

    let left: number;
    let top: number;
    let width: number;
    let height: number;

    function setSelection(): void {
        const containerRect = container.getBoundingClientRect();
        const imageRect = image!.getBoundingClientRect();

        const containerLeft = containerRect.left;
        const containerTop = containerRect.top;

        left = imageRect!.left - containerLeft;
        top = imageRect!.top - containerTop;
        width = image!.clientWidth;
        height = image!.clientHeight;
    }

    export function updateSelection(): Promise<void> {
        let updateResolve: () => void;
        const afterUpdate: Promise<void> = new Promise((resolve) => {
            updateResolve = resolve;
        });

        setSelection();
        setTimeout(() => updateResolve());

        return afterUpdate;
    }

    const dispatch = createEventDispatcher();

    let selectionRef: HTMLDivElement;
    function initSelection(selection: HTMLDivElement): void {
        setSelection();
        selectionRef = selection;
    }

    onMount(() => dispatch("mount", { selection: selectionRef }));
</script>

<div
    use:initSelection
    on:click={(event) =>
        /* prevent triggering Bootstrap dropdown */ event.stopImmediatePropagation()}
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
