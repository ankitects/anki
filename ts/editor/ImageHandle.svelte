<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ImageHandleFloat from "./ImageHandleFloat.svelte";
    import ImageHandleSizeSelect from "./ImageHandleSizeSelect.svelte";

    import { onDestroy, getContext } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let container: HTMLElement;
    export let sheet: CSSStyleSheet;
    export let activeImage: HTMLImageElement | null = null;
    export let isRtl: boolean = false;

    $: naturalWidth = activeImage?.naturalWidth;
    $: naturalHeight = activeImage?.naturalHeight;
    $: aspectRatio = naturalWidth && naturalHeight ? naturalWidth / naturalHeight : NaN;

    $: showDimensions = activeImage ? Number(activeImage!.height) >= 50 : false;

    $: showFloat = activeImage ? Number(activeImage!.width) >= 100 : false;

    let actualWidth = "";
    let actualHeight = "";
    let customDimensions = false;

    let containerTop = 0;
    let containerLeft = 0;

    let top = 0;
    let left = 0;
    let width = 0;
    let height = 0;

    $: if (activeImage) {
        updateSizes();
    } else {
        resetSizes();
    }

    const resizeObserver = new ResizeObserver(() => {
        if (activeImage) {
            updateSizes();
        }
    });

    function startObserving() {
        resizeObserver.observe(container);
    }

    function stopObserving() {
        resizeObserver.unobserve(container);
    }

    startObserving();

    function resetSizes() {
        top = 0;
        left = 0;
        width = 0;
        height = 0;
    }

    function updateSizes() {
        const containerRect = container.getBoundingClientRect();
        const imageRect = activeImage!.getBoundingClientRect();

        containerTop = containerRect.top;
        containerLeft = containerRect.left;
        top = imageRect!.top - containerTop;
        left = imageRect!.left - containerLeft;
        width = activeImage!.clientWidth;
        height = activeImage!.clientHeight;

        /* we do not want the actual width, but rather the intended display width */
        const widthProperty = activeImage!.style.width;
        let inPixel = false;
        customDimensions = false;

        if (widthProperty) {
            if (widthProperty.endsWith("px")) {
                actualWidth = widthProperty.substring(0, widthProperty.length - 2);
                inPixel = true;
            } else {
                actualWidth = widthProperty;
            }

            customDimensions = true;
        } else {
            actualWidth = String(naturalWidth);
        }

        const heightProperty = activeImage!.style.height;
        if (inPixel || heightProperty === "auto") {
            actualHeight = String(Math.trunc(Number(actualWidth) / aspectRatio));
        } else if (heightProperty) {
            actualHeight = heightProperty.endsWith("px")
                ? heightProperty.substring(0, heightProperty.length - 2)
                : heightProperty;
            customDimensions = true;
        } else {
            actualHeight = String(naturalHeight);
        }
    }

    /* memoized position of image on resize start
     * prevents frantic behavior when image shift into the next/previous line */
    let getDragWidth: (event: PointerEvent) => number;
    let getDragHeight: (event: PointerEvent) => number;

    const setPointerCapture =
        (north: boolean, west: boolean) =>
        (event: PointerEvent): void => {
            if (!active || event.pointerId !== 1) {
                return;
            }

            const containerRect = container.getBoundingClientRect();
            const imageRect = activeImage!.getBoundingClientRect();

            const originalContainerY = containerRect.top;
            const originalContainerX = containerRect.left;
            const originalY = imageRect!.top - containerTop;
            const originalX = imageRect!.left - containerLeft;

            getDragWidth = (event) =>
                west
                    ? activeImage!.clientWidth -
                      event.clientX +
                      (originalContainerX + originalX)
                    : event.clientX - originalContainerX - originalX;

            getDragHeight = (event) =>
                north
                    ? activeImage!.clientHeight -
                      event.clientY +
                      (originalContainerY + originalY)
                    : event.clientY - originalContainerY - originalY;

            stopObserving();
            (event.target as Element).setPointerCapture(event.pointerId);
        };

    function resize(event: PointerEvent): void {
        const element = event.target! as Element;

        if (!element.hasPointerCapture(event.pointerId)) {
            return;
        }

        const dragWidth = getDragWidth(event);
        const dragHeight = getDragHeight(event);

        const widthIncrease = dragWidth / naturalWidth!;
        const heightIncrease = dragHeight / naturalHeight!;

        if (widthIncrease > heightIncrease) {
            width = Math.max(Math.trunc(dragWidth), 12);
            height = Math.trunc(naturalHeight! * (width / naturalWidth!));
        } else {
            height = Math.max(Math.trunc(dragHeight), 10);
            width = Math.trunc(naturalWidth! * (height / naturalHeight!));
        }

        showDimensions = height >= 50;
        showFloat = width >= 100;

        activeImage!.width = width;
    }

    let sizeSelect: any;
    let active = false;

    function onDblclick() {
        sizeSelect.toggleActualSize();
    }

    const nightMode = getContext(nightModeKey);

    onDestroy(() => resizeObserver.disconnect());
</script>

<div
    style="--top: {top}px; --left: {left}px; --width: {width}px; --height: {height}px;"
    class="image-handle-selection"
>
    {#if activeImage}
        <div
            class="image-handle-bg"
            on:mousedown|preventDefault
            on:dblclick={onDblclick}
        />

        {#if showFloat}
            <div class="image-handle-float" class:is-rtl={isRtl} on:click={updateSizes}>
                <ImageHandleFloat {activeImage} {isRtl} />
            </div>
        {/if}

        {#if showDimensions}
            <div class="image-handle-dimensions" class:is-rtl={isRtl}>
                <span>{actualWidth}&times;{actualHeight}</span>
                {#if customDimensions}<span
                        >(Original: {naturalWidth}&times;{naturalHeight})</span
                    >{/if}
            </div>
        {/if}
    {/if}

    {#if sheet}
        <div class="image-handle-size-select" class:is-rtl={isRtl}>
            <ImageHandleSizeSelect
                bind:this={sizeSelect}
                bind:active
                {container}
                {sheet}
                {activeImage}
                {isRtl}
                on:update={updateSizes}
            />
        </div>
    {/if}

    {#if activeImage}
        <div
            class:nightMode
            class:active
            class="image-handle-control image-handle-control-nw"
            on:mousedown|preventDefault
            on:pointerdown={setPointerCapture(true, true)}
            on:pointerup={startObserving}
            on:pointermove={resize}
        />
        <div
            class:nightMode
            class:active
            class="image-handle-control image-handle-control-ne"
            on:mousedown|preventDefault
            on:pointerdown={setPointerCapture(true, false)}
            on:pointerup={startObserving}
            on:pointermove={resize}
        />
        <div
            class:nightMode
            class:active
            class="image-handle-control image-handle-control-sw"
            on:mousedown|preventDefault
            on:pointerdown={setPointerCapture(false, true)}
            on:pointerup={startObserving}
            on:pointermove={resize}
        />
        <div
            class:nightMode
            class:active
            class="image-handle-control image-handle-control-se"
            on:mousedown|preventDefault
            on:pointerdown={setPointerCapture(false, false)}
            on:pointerup={startObserving}
            on:pointermove={resize}
        />
    {/if}
</div>

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

    .image-handle-float {
        top: 3px;
        left: 3px;

        &.is-rtl {
            left: auto;
            right: 3px;
        }
    }

    .image-handle-size-select {
        top: 3px;
        right: 3px;

        &.is-rtl {
            right: auto;
            left: 3px;
        }
    }

    .image-handle-dimensions {
        pointer-events: none;
        user-select: none;

        font-size: 13px;
        color: white;
        background-color: rgba(0 0 0 / 0.3);
        border-color: black;
        border-radius: 0.25rem;
        padding: 0 5px;

        bottom: 3px;
        right: 3px;
        margin-left: 3px;

        &.is-rtl {
            right: auto;
            left: 3px;
            margin-right: 3px;
        }
    }

    .image-handle-control {
        width: 7px;
        height: 7px;
        border: 1px solid black;

        &.active {
            background-color: black;
        }

        &.nightMode {
            border-color: white;

            &.active {
                background-color: white;
            }
        }
    }

    .image-handle-control-nw {
        top: -5px;
        left: -5px;
        border-bottom: none;
        border-right: none;

        &.active {
            cursor: nw-resize;
        }
    }

    .image-handle-control-ne {
        top: -5px;
        right: -5px;
        border-bottom: none;
        border-left: none;

        &.active {
            cursor: ne-resize;
        }
    }

    .image-handle-control-sw {
        bottom: -5px;
        left: -5px;
        border-top: none;
        border-right: none;

        &.active {
            cursor: sw-resize;
        }
    }

    .image-handle-control-se {
        bottom: -5px;
        right: -5px;
        border-top: none;
        border-left: none;

        &.active {
            cursor: se-resize;
        }
    }
</style>
