<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import HandleBackground from "./HandleBackground.svelte";
    import HandleSelection from "./HandleSelection.svelte";
    import HandleControl from "./HandleControl.svelte";
    import HandleLabel from "./HandleLabel.svelte";
    import ImageHandleFloat from "./ImageHandleFloat.svelte";
    import ImageHandleSizeSelect from "./ImageHandleSizeSelect.svelte";

    import { onDestroy } from "svelte";

    export let container: HTMLElement;
    export let sheet: CSSStyleSheet;
    export let activeImage: HTMLImageElement | null = null;
    export let isRtl: boolean = false;

    $: naturalWidth = activeImage?.naturalWidth;
    $: naturalHeight = activeImage?.naturalHeight;
    $: aspectRatio = naturalWidth && naturalHeight ? naturalWidth / naturalHeight : NaN;
    $: showFloat = activeImage ? Number(activeImage!.width) >= 100 : false;

    let customDimensions: boolean = false;
    let actualWidth = "";
    let actualHeight = "";

    function updateDimensions() {
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
    }

    let updateSelection: () => void;

    async function updateSizesWithDimensions() {
        updateSelection();
        updateDimensions();
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

    function setPointerCapture({ detail }: CustomEvent): void {
        if (!active || detail.originalEvent.pointerId !== 1) {
            return;
        }

        const imageRect = activeImage!.getBoundingClientRect();

        const imageLeft = imageRect!.left;
        const imageRight = imageRect!.right;
        const [multX, imageX] = detail.west ? [-1, imageRight] : [1, -imageLeft];

        getDragWidth = ({ clientX }) => multX * clientX + imageX;

        const imageTop = imageRect!.top;
        const imageBottom = imageRect!.bottom;
        const [multY, imageY] = detail.north ? [-1, imageBottom] : [1, -imageTop];

        getDragHeight = ({ clientY }) => multY * clientY + imageY;

        stopObserving();

        const target = detail.originalEvent.target as Element;
        target.setPointerCapture(detail.originalEvent.pointerId);
    }

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

        let width: number;

        if (widthIncrease > heightIncrease) {
            width = Math.max(Math.trunc(dragWidth), minResizeWidth);
        } else {
            let height = Math.max(Math.trunc(dragHeight), minResizeHeight);
            width = Math.trunc(naturalWidth! * (height / naturalHeight!));
        }

        showFloat = width >= 100;
        activeImage!.width = width;

        await updateSizesWithDimensions();
    }

    let toggleActualSize: () => void;
    let active = false;

    onDestroy(() => resizeObserver.disconnect());
</script>

<HandleSelection bind:updateSelection {container} {activeImage}>
    {#if activeImage}
        <HandleBackground on:dblclick={toggleActualSize} />
    {/if}

    {#if sheet}
        <div class="image-handle-size-select" class:is-rtl={isRtl}>
            <ImageHandleSizeSelect
                bind:toggleActualSize
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
        {#if showFloat}
            <div
                class="image-handle-float"
                class:is-rtl={isRtl}
                on:click={updateSizesWithDimensions}
            >
                <ImageHandleFloat {activeImage} {isRtl} />
            </div>
        {/if}

        <HandleLabel {isRtl} on:mount={updateDimensions}>
            <span>{actualWidth}&times;{actualHeight}</span>
            {#if customDimensions}
                <span>(Original: {naturalWidth}&times;{naturalHeight})</span>
            {/if}
        </HandleLabel>

        <HandleControl
            {active}
            activeSize={7}
            offsetX={5}
            offsetY={5}
            on:pointerclick={setPointerCapture}
            on:pointerup={startObserving}
            on:pointermove={resize}
        />
    {/if}
</HandleSelection>

<style lang="scss">
    div {
        position: absolute;
    }

    .image-handle-float {
        left: 3px;
        top: 3px;

        &.is-rtl {
            left: auto;
            right: 3px;
        }
    }

    .image-handle-size-select {
        right: 3px;
        top: 3px;

        &.is-rtl {
            right: auto;
            left: 3px;
        }
    }
</style>
