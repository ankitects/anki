<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import ImageHandleButtons from "./ImageHandleButtons.svelte";
    import { getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let image: HTMLImageElement | null = null;
    export let container: HTMLElement | null = null;

    let hidden = true;

    let naturalWidth = 0;
    let naturalHeight = 0;

    let containerTop = 0;
    let containerLeft = 0;

    let top = 0;
    let left = 0;
    let width = 0;
    let height = 0;

    $: if (image) {
        const imageRect = image.getBoundingClientRect();
        const containerRect = (
            container ?? document.documentElement
        ).getBoundingClientRect();

        naturalWidth = image.naturalWidth;
        naturalHeight = image.naturalHeight;

        containerTop = containerRect.top;
        containerLeft = containerRect.left;
        top = imageRect.top - containerTop;
        left = imageRect.left - containerLeft;
        width = image.clientWidth;
        height = image.clientHeight;
        hidden = false;
    } else {
        hidden = true;
    }

    function setPointerCapture(event: PointerEvent): void {
        if (event.pointerId !== 1) {
            return;
        }

        (event.target as Element).setPointerCapture(event.pointerId);
    }

    function resize(event: PointerEvent): void {
        if (!(event.target as Element).hasPointerCapture(event.pointerId)) {
            return;
        }

        const dragWidth = event.clientX - containerLeft - left;
        const dragHeight = event.clientY - containerTop - top;

        const widthIncrease = dragWidth / naturalWidth;
        const heightIncrease = dragHeight / naturalHeight;

        if (widthIncrease > heightIncrease) {
            width = dragWidth;
            height = naturalHeight * widthIncrease;
        } else {
            height = dragHeight;
            width = naturalWidth * heightIncrease;
        }

        image!.style.width = `${width}px`;
        image!.style.height = `${height}px`;
    }

    const nightMode = getContext(nightModeKey);
</script>

{#if image}
    <div
        style="--top: {top}px; --left: {left}px; --width: {width}px; --height: {height}px;"
        class="image-handle-selection"
        {hidden}
    >
        <div class="image-handle-bg" />
        <div class="image-handle-buttons">
            <ImageHandleButtons bind:float={image.style.float} />
        </div>
        <div class="image-handle-dimensions">
            {width}&times;{height} (Original: {naturalWidth}&times;{naturalHeight})
        </div>
        <div class:nightMode class="image-handle-control image-handle-control-nw" />
        <div class:nightMode class="image-handle-control image-handle-control-ne" />
        <div
            class:nightMode
            class="image-handle-control image-handle-control-sw is-active"
            on:pointerdown={setPointerCapture}
            on:pointermove={resize}
        />
        <div
            class:nightMode
            class="image-handle-control image-handle-control-se is-active"
            on:pointerdown={setPointerCapture}
            on:pointermove={resize}
        />
    </div>
{/if}

<style lang="scss">
    div {
        position: absolute;
    }

    .image-handle-selection {
        top: var(--top);
        left: var(--left);
        width: var(--width);
        height: var(--height);
    }

    .image-handle-bg {
        width: 100%;
        height: 100%;
        background-color: black;
        opacity: 0.2;
    }

    .image-handle-buttons {
        top: 3px;
        left: 3px;
    }

    .image-handle-dimensions {
        pointer-events: none;
        user-select: none;
        bottom: 3px;
        right: 3px;

        background-color: rgba(0 0 0 / 0.3);
        border-color: black;
        border-radius: 0.25rem;
        padding: 0 5px;
    }

    .image-handle-control {
        width: 7px;
        height: 7px;
        border: 1px solid black;

        &.is-active {
            background-color: black;
        }

        &.nightMode {
            border-color: white;

            &.is-active {
                background-color: white;
            }
        }
    }

    .image-handle-control-nw {
        top: -5px;
        left: -5px;
        border-bottom: none;
        border-right: none;
    }

    .image-handle-control-ne {
        top: -5px;
        right: -5px;
        border-bottom: none;
        border-left: none;
    }

    .image-handle-control-sw {
        bottom: -5px;
        left: -5px;
        border-top: none;
        border-right: none;

        &.is-active {
            cursor: sw-resize;
        }
    }

    .image-handle-control-se {
        bottom: -5px;
        right: -5px;
        border-top: none;
        border-left: none;

        &.is-active {
            cursor: se-resize;
        }
    }
</style>
