<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import HandleSelection from "./HandleSelection.svelte";
    import ImageHandleFloat from "./ImageHandleFloat.svelte";
    import ImageHandleSizeSelect from "./ImageHandleSizeSelect.svelte";

    import { onDestroy, getContext, tick } from "svelte";
    import { nightModeKey } from "components/context-keys";

    export let container: HTMLElement;
    export let sheet: CSSStyleSheet;
    export let activeImage: HTMLImageElement | null = null;
    export let isRtl: boolean = false;

    $: naturalWidth = activeImage?.naturalWidth;
    $: naturalHeight = activeImage?.naturalHeight;
    $: aspectRatio = naturalWidth && naturalHeight ? naturalWidth / naturalHeight : NaN;
    $: showFloat = activeImage ? Number(activeImage!.width) >= 100 : false;

    /* SIZES */
    let containerLeft = 0;
    let containerTop = 0;

    let left = 0;
    let top = 0;
    let width = 0;
    let height = 0;

    function updateSizes() {
        const containerRect = container.getBoundingClientRect();
        const imageRect = activeImage!.getBoundingClientRect();

        containerLeft = containerRect.left;
        containerTop = containerRect.top;

        left = imageRect!.left - containerLeft;
        top = imageRect!.top - containerTop;
        width = activeImage!.clientWidth;
        height = activeImage!.clientHeight;
    }

    function resetSizes() {
        activeImage = null;

        left = 0;
        top = 0;
        width = 0;
        height = 0;
    }

    let actualWidth = "";
    let actualHeight = "";
    let customDimensions = false;
    let overflowFix = 0;

    function updateDimensions(dimensions: HTMLDivElement) {
        /* we do not want the actual width, but rather the intended display width */
        const widthAttribute = activeImage!.getAttribute("width");
        customDimensions = false;

        if (widthAttribute) {
            actualWidth = widthAttribute;
            customDimensions = true;
        } else {
            actualWidth = String(naturalWidth);
        }

        const heightAttribute = activeImage!.getAttribute("height");
        if (heightAttribute) {
            actualHeight = heightAttribute;
            customDimensions = true;
        } else if (customDimensions) {
            actualHeight = String(Math.trunc(Number(actualWidth) / aspectRatio));
        } else {
            actualHeight = String(naturalHeight);
        }

        tick().then(() => {
            const boundingClientRect = dimensions.getBoundingClientRect();
            const overflow = isRtl
                ? window.innerWidth - boundingClientRect.x - boundingClientRect.width
                : boundingClientRect.x;

            overflowFix = Math.min(0, overflowFix + overflow, overflow);
        });
    }

    let dimensions: HTMLDivElement;

    async function updateSizesWithDimensions() {
        updateSizes();
        updateDimensions(dimensions);
    }

    /* window resizing */
    const resizeObserver = new ResizeObserver(async () => {
        if (activeImage) {
            await updateSizesWithDimensions();
        }
    });

    function startObserving() {
        resizeObserver.observe(container);
    }

    function stopObserving() {
        resizeObserver.unobserve(container);
    }

    startObserving();

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

            const imageRect = activeImage!.getBoundingClientRect();

            const imageLeft = imageRect!.left;
            const imageRight = imageRect!.right;
            const [multX, imageX] = west ? [-1, imageRight] : [1, -imageLeft];

            getDragWidth = ({ clientX }) => multX * clientX + imageX;

            const imageTop = imageRect!.top;
            const imageBottom = imageRect!.bottom;
            const [multY, imageY] = north ? [-1, imageBottom] : [1, -imageTop];

            getDragHeight = ({ clientY }) => multY * clientY + imageY;

            stopObserving();
            (event.target as Element).setPointerCapture(event.pointerId);
        };

    $: [minResizeWidth, minResizeHeight] =
        aspectRatio > 1 ? [5 * aspectRatio, 5] : [5, 5 / aspectRatio];

    async function resize(event: PointerEvent) {
        const element = event.target! as Element;

        if (!element.hasPointerCapture(event.pointerId)) {
            return;
        }

        const dragWidth = getDragWidth(event);
        const dragHeight = getDragHeight(event);

        const widthIncrease = dragWidth / naturalWidth!;
        const heightIncrease = dragHeight / naturalHeight!;

        if (widthIncrease > heightIncrease) {
            width = Math.max(Math.trunc(dragWidth), minResizeWidth);
            height = Math.trunc(naturalHeight! * (width / naturalWidth!));
        } else {
            height = Math.max(Math.trunc(dragHeight), minResizeHeight);
            width = Math.trunc(naturalWidth! * (height / naturalHeight!));
        }

        showFloat = width >= 100;
        activeImage!.width = width;

        await updateSizesWithDimensions();
    }

    let sizeSelect: any;
    let active = false;

    $: if (activeImage && sizeSelect?.images.includes(activeImage)) {
        updateSizes();
    } else {
        resetSizes();
    }

    function onDblclick() {
        sizeSelect.toggleActualSize();
    }

    const nightMode = getContext(nightModeKey);

    onDestroy(() => resizeObserver.disconnect());
</script>

<HandleSelection
    --left="{left}px"
    --top="{top}px"
    --width="{width}px"
    --height="{height}px"
>
    {#if activeImage}
        <div
            class="image-handle-bg"
            on:mousedown|preventDefault
            on:dblclick={onDblclick}
        />

        {#if showFloat}
            <div
                class="image-handle-float"
                class:is-rtl={isRtl}
                on:click={updateSizesWithDimensions}
            >
                <ImageHandleFloat {activeImage} {isRtl} />
            </div>
        {/if}

        <div
            bind:this={dimensions}
            class="image-handle-dimensions"
            class:is-rtl={isRtl}
            style="--overflow-fix: {overflowFix}px"
            use:updateDimensions
        >
            <span>{actualWidth}&times;{actualHeight}</span>
            {#if customDimensions}<span
                    >(Original: {naturalWidth}&times;{naturalHeight})</span
                >{/if}
        </div>
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
                on:update={updateSizesWithDimensions}
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
</HandleSelection>

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
        margin-right: var(--overflow-fix, 0);

        &.is-rtl {
            right: auto;
            left: 3px;
            margin-right: 3px;
            margin-left: var(--overflow-fix, 0);
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
