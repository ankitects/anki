<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts" context="module">
    import { writable } from "svelte/store";

    export const shrinkImagesByDefault = writable(true);
</script>

<script lang="ts">
    import * as tr from "@generated/ftl";
    import { on } from "@tslib/events";
    import { removeStyleProperties } from "@tslib/styling";
    import type { Callback } from "@tslib/typing";
    import { tick } from "svelte";

    import ButtonToolbar from "$lib/components/ButtonToolbar.svelte";
    import Popover from "$lib/components/Popover.svelte";
    import WithFloating from "$lib/components/WithFloating.svelte";
    import WithOverlay from "$lib/components/WithOverlay.svelte";

    import type { EditingInputAPI } from "../EditingArea.svelte";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";
    import HandleLabel from "../HandleLabel.svelte";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import FloatButtons from "./FloatButtons.svelte";
    import SizeSelect from "./SizeSelect.svelte";

    export let maxWidth: number;
    export let maxHeight: number;

    (<[string, number][]>[
        ["--editor-shrink-max-width", maxWidth],
        ["--editor-shrink-max-height", maxHeight],
        ["--editor-default-max-width", maxWidth],
        ["--editor-default-max-height", maxHeight],
    ]).forEach(([prop, value]) =>
        document.documentElement.style.setProperty(prop, `${value}px`),
    );

    $: document.documentElement.classList.toggle(
        "shrink-image",
        $shrinkImagesByDefault,
    );

    const { focusedInput } = context.get();

    let cleanup: Callback;

    async function initialize(input: EditingInputAPI | null): Promise<void> {
        cleanup?.();

        if (!input || !editingInputIsRichText(input)) {
            return;
        }

        cleanup = on(await input.element, "click", maybeShowHandle);
    }

    $: initialize($focusedInput);

    let activeImage: HTMLImageElement | null = null;

    /**
     * Returns the value if set, otherwise null.
     */
    function getBooleanDatasetAttribute(
        element: HTMLElement | SVGElement,
        attribute: string,
    ): boolean | null {
        return attribute in element.dataset
            ? element.dataset[attribute] !== "false"
            : null;
    }

    let isSizeConstrained = false;
    $: {
        if (activeImage) {
            isSizeConstrained =
                getBooleanDatasetAttribute(activeImage, "editorShrink") ??
                $shrinkImagesByDefault;
        }
    }

    async function resetHandle(): Promise<void> {
        activeImage = null;
        await tick();
    }

    let naturalWidth: number;
    let naturalHeight: number;
    let aspectRatio: number;

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

    async function maybeShowHandle(event: Event): Promise<void> {
        if (event.target instanceof HTMLImageElement) {
            const image = event.target;

            if (!image.dataset.anki) {
                activeImage = image;

                naturalWidth = activeImage?.naturalWidth;
                naturalHeight = activeImage?.naturalHeight;
                aspectRatio =
                    naturalWidth && naturalHeight ? naturalWidth / naturalHeight : NaN;

                updateDimensions();
            }
        }
    }

    let customDimensions: boolean = false;
    let actualWidth = "";
    let actualHeight = "";

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
        removeStyleProperties(activeImage!, "width", "height");
        activeImage!.width = width;
    }

    function toggleActualSize(): void {
        if (isSizeConstrained) {
            activeImage!.dataset.editorShrink = "false";
        } else {
            activeImage!.dataset.editorShrink = "true";
        }

        isSizeConstrained = !isSizeConstrained;
    }

    function clearActualSize(): void {
        activeImage!.removeAttribute("width");
    }

    let shrinkingDisabled: boolean;
    $: shrinkingDisabled =
        Number(actualWidth) <= maxWidth && Number(actualHeight) <= maxHeight;

    let restoringDisabled: boolean;
    $: restoringDisabled = !(activeImage?.hasAttribute("width") ?? true);

    const widthObserver = new MutationObserver(() => {
        restoringDisabled = !activeImage!.hasAttribute("width");
        updateDimensions();
    });

    $: activeImage
        ? widthObserver.observe(activeImage, {
              attributes: true,
              attributeFilter: ["width"],
          })
        : widthObserver.disconnect();

    let imageOverlay: HTMLElement;
</script>

<div bind:this={imageOverlay} class="image-overlay">
    {#if activeImage}
        <WithOverlay reference={activeImage} inline let:position={positionOverlay}>
            <WithFloating
                reference={activeImage}
                offset={20}
                inline
                hideIfReferenceHidden
                portalTarget={document.body}
                on:close={async ({ detail }) => {
                    const { reason, originalEvent } = detail;

                    if (reason === "outsideClick") {
                        // If the click is still in the overlay, we do not want
                        // to reset the handle either
                        if (!originalEvent?.composedPath().includes(imageOverlay)) {
                            await resetHandle();
                        }
                    } else {
                        await resetHandle();
                    }
                }}
            >
                <Popover slot="floating" let:position={positionFloating}>
                    <ButtonToolbar>
                        <FloatButtons
                            image={activeImage}
                            on:update={async () => {
                                positionOverlay();
                                positionFloating();
                            }}
                        />

                        <SizeSelect
                            {shrinkingDisabled}
                            {restoringDisabled}
                            {isSizeConstrained}
                            on:imagetoggle={() => {
                                toggleActualSize();
                                positionOverlay();
                            }}
                            on:imageclear={() => {
                                clearActualSize();
                                positionOverlay();
                            }}
                        />
                    </ButtonToolbar>
                </Popover>
            </WithFloating>

            <svelte:fragment slot="overlay" let:position={positionOverlay}>
                <HandleBackground
                    on:dblclick={() => {
                        if (shrinkingDisabled) {
                            return;
                        }
                        toggleActualSize();
                        positionOverlay();
                    }}
                />

                <HandleLabel>
                    {#if isSizeConstrained}
                        <span>{`(${tr.editingDoubleClickToExpand()})`}</span>
                    {:else}
                        <span>{actualWidth}&times;{actualHeight}</span>
                        {#if customDimensions}
                            <span>
                                (Original: {naturalWidth}&times;{naturalHeight})
                            </span>
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
                    }}
                />
            </svelte:fragment>
        </WithOverlay>
    {/if}
</div>
