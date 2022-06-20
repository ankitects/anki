<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { onMount, tick } from "svelte";

    import ButtonDropdown from "../../components/ButtonDropdown.svelte";
    import WithDropdown from "../../components/WithDropdown.svelte";
    import { on } from "../../lib/events";
    import * as tr from "../../lib/ftl";
    import { singleCallback } from "../../lib/typing";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import HandleLabel from "../HandleLabel.svelte";
    import HandleSelection from "../HandleSelection.svelte";
    import { context } from "../rich-text-input";
    import FloatButtons from "./FloatButtons.svelte";
    import SizeSelect from "./SizeSelect.svelte";

    export let maxWidth: number;
    export let maxHeight: number;

    const { element } = context.get();

    let activeImage: HTMLImageElement | null = null;

    /**
     * For element dataset attributes which work like the contenteditable attribute
     */
    function isDatasetAttributeFlagSet(
        element: HTMLElement | SVGElement,
        attribute: string,
    ): boolean {
        return attribute in element.dataset && element.dataset[attribute] !== "false";
    }

    $: isSizeConstrained = activeImage
        ? isDatasetAttributeFlagSet(activeImage, "editorShrink")
        : false;

    async function resetHandle(): Promise<void> {
        activeImage = null;
        await tick();
    }

    async function maybeShowHandle(event: Event): Promise<void> {
        await resetHandle();

        if (event.target instanceof HTMLImageElement) {
            const image = event.target;

            if (!image.dataset.anki) {
                activeImage = image;
            }
        }
    }

    $: naturalWidth = activeImage?.naturalWidth;
    $: naturalHeight = activeImage?.naturalHeight;
    $: aspectRatio = naturalWidth && naturalHeight ? naturalWidth / naturalHeight : NaN;

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

    let updateSelection: () => Promise<void>;

    async function updateSizesWithDimensions() {
        await updateSelection?.();
        updateDimensions();
    }

    /* window resizing */
    const resizeObserver = new ResizeObserver(async () => {
        await updateSizesWithDimensions();
    });

    $: observes = Boolean(activeImage);

    async function toggleResizeObserver(observes: boolean) {
        const container = await element;

        if (observes) {
            resizeObserver.observe(container);
        } else {
            resizeObserver.unobserve(container);
        }
    }

    $: toggleResizeObserver(observes);

    /* memoized position of image on resize start
     * prevents frantic behavior when image shift into the next/previous line */
    let getDragWidth: (event: PointerEvent) => number;
    let getDragHeight: (event: PointerEvent) => number;

    function setPointerCapture({ detail }: CustomEvent): void {
        const pointerId = detail.originalEvent.pointerId;

        if (pointerId !== 1) {
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

        const target = detail.originalEvent.target as Element;
        target.setPointerCapture(pointerId);
    }

    let minResizeWidth: number;
    let minResizeHeight: number;
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
            const height = Math.max(Math.trunc(dragHeight), minResizeHeight);
            width = Math.trunc(naturalWidth! * (height / naturalHeight!));
        }

        /**
         * Image resizing add-ons previously used image.style.width/height to set the
         * preferred dimension of an image. In these cases, if we'd only set
         * image.[dimension], there would be no visible effect on the image.
         * To avoid confusion with users we'll clear image.style.[dimension] (for now).
         */
        activeImage!.style.removeProperty("width");
        activeImage!.style.removeProperty("height");
        if (activeImage!.getAttribute("style")?.length === 0) {
            activeImage!.removeAttribute("style");
        }

        activeImage!.width = width;
    }

    function toggleActualSize(): void {
        if (isSizeConstrained) {
            delete activeImage!.dataset.editorShrink;
        } else {
            activeImage!.dataset.editorShrink = "true";
        }

        isSizeConstrained = !isSizeConstrained;
    }

    function clearActualSize(): void {
        activeImage!.removeAttribute("width");
    }

    onMount(async () => {
        const container = await element;

        container.style.setProperty("--editor-shrink-max-width", `${maxWidth}px`);
        container.style.setProperty("--editor-shrink-max-height", `${maxHeight}px`);

        return singleCallback(
            on(container, "click", maybeShowHandle),
            on(container, "blur", resetHandle),
            on(container, "key" as any, resetHandle),
            on(container, "paste", resetHandle),
        );
    });

    let shrinkingDisabled: boolean;
    $: shrinkingDisabled =
        Number(actualWidth) <= maxWidth && Number(actualHeight) <= maxHeight;

    let restoringDisabled: boolean;
    $: restoringDisabled = !activeImage?.hasAttribute("width") ?? true;

    const widthObserver = new MutationObserver(
        () => (restoringDisabled = !activeImage!.hasAttribute("width")),
    );

    $: activeImage
        ? widthObserver.observe(activeImage, {
              attributes: true,
              attributeFilter: ["width"],
          })
        : widthObserver.disconnect();
</script>

<WithDropdown
    drop="down"
    autoOpen={true}
    autoClose={false}
    distance={3}
    let:createDropdown
    let:dropdownObject
>
    {#if activeImage}
        {#await element then container}
            <HandleSelection
                bind:updateSelection
                {container}
                image={activeImage}
                on:mount={(event) => createDropdown(event.detail.selection)}
            >
                <HandleBackground
                    on:dblclick={() => {
                        if (shrinkingDisabled) {
                            return;
                        }
                        toggleActualSize();
                        updateSizesWithDimensions();
                        dropdownObject.update();
                    }}
                />

                <HandleLabel on:mount={updateDimensions}>
                    {#if isSizeConstrained}
                        <span>{tr.editingDoubleClickToExpand()}</span>
                    {:else}
                        <span>{actualWidth}&times;{actualHeight}</span>
                        {#if customDimensions}
                            <span>(Original: {naturalWidth}&times;{naturalHeight})</span
                            >
                        {/if}
                    {/if}
                </HandleLabel>

                <HandleControl
                    active={!isSizeConstrained}
                    activeSize={8}
                    offsetX={5}
                    offsetY={5}
                    on:pointerclick={(event) => {
                        if (!isSizeConstrained) {
                            setPointerCapture(event);
                        }
                    }}
                    on:pointermove={(event) => {
                        resize(event);
                        updateSizesWithDimensions();
                        dropdownObject.update();
                    }}
                />
            </HandleSelection>
        {/await}

        <ButtonDropdown on:click={updateSizesWithDimensions}>
            <FloatButtons image={activeImage} on:update={dropdownObject.update} />
            <SizeSelect
                {shrinkingDisabled}
                {restoringDisabled}
                {isSizeConstrained}
                on:imagetoggle={() => {
                    toggleActualSize();
                    updateSizesWithDimensions();
                    dropdownObject.update();
                }}
                on:imageclear={() => {
                    clearActualSize();
                    updateSizesWithDimensions();
                    dropdownObject.update();
                }}
            />
        </ButtonDropdown>
    {/if}
</WithDropdown>
