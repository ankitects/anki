<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "components/WithDropdown.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import Item from "components/Item.svelte";

    import HandleBackground from "../HandleBackground.svelte";
    import HandleSelection from "../HandleSelection.svelte";
    import HandleControl from "../HandleControl.svelte";
    import HandleLabel from "../HandleLabel.svelte";

    import WithImageConstrained from "./WithImageConstrained.svelte";
    import FloatButtons from "./FloatButtons.svelte";
    import SizeSelect from "./SizeSelect.svelte";

    import { tick, onDestroy } from "svelte";
    import type CustomStyles from "editable/CustomStyles.svelte";
    import type { StyleObject } from "editable/CustomStyles.svelte";

    export let customStyles: CustomStyles;

    const sheetPromise = customStyles
        .addStyleTag("imageOverlay")
        .then((styleObject: StyleObject) => styleObject.element.sheet!);

    export let container: HTMLElement;

    let activeImage: HTMLImageElement | null = null;

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

    container.addEventListener("click", maybeShowHandle);
    container.addEventListener("key", resetHandle);
    container.addEventListener("paste", resetHandle);

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
    $: if (observes) {
        resizeObserver.observe(container);
    } else {
        resizeObserver.unobserve(container);
    }

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

        activeImage!.width = width;
    }

    onDestroy(() => {
        resizeObserver.disconnect();
        container.removeEventListener("click", maybeShowHandle);
        container.removeEventListener("key", resetHandle);
        container.removeEventListener("paste", resetHandle);
    });
</script>

<WithDropdown
    drop="down"
    autoOpen={true}
    autoClose={false}
    distance={3}
    let:createDropdown
    let:dropdownObject
>
    {#await sheetPromise then sheet}
        <WithImageConstrained
            {sheet}
            {container}
            {activeImage}
            maxWidth={250}
            maxHeight={125}
            on:update={() => {
                updateSizesWithDimensions();
                dropdownObject.update();
            }}
            let:toggleActualSize
            let:active
        >
            {#if activeImage}
                <HandleSelection
                    bind:updateSelection
                    {container}
                    image={activeImage}
                    on:mount={(event) => createDropdown(event.detail.selection)}
                >
                    <HandleBackground on:dblclick={toggleActualSize} />

                    <HandleLabel on:mount={updateDimensions}>
                        <span>{actualWidth}&times;{actualHeight}</span>
                        {#if customDimensions}
                            <span>(Original: {naturalWidth}&times;{naturalHeight})</span
                            >
                        {/if}
                    </HandleLabel>

                    <HandleControl
                        {active}
                        activeSize={8}
                        offsetX={5}
                        offsetY={5}
                        on:pointerclick={(event) => {
                            if (active) {
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
                <ButtonDropdown>
                    <div on:click={updateSizesWithDimensions}>
                        <Item>
                            <FloatButtons
                                image={activeImage}
                                on:update={dropdownObject.update}
                            />
                        </Item>
                        <Item>
                            <SizeSelect {active} on:click={toggleActualSize} />
                        </Item>
                    </div>
                </ButtonDropdown>
            {/if}
        </WithImageConstrained>
    {/await}
</WithDropdown>
