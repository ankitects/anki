<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "components/WithDropdown.svelte";
    import ButtonToolbar from "components/ButtonToolbar.svelte";
    import DropdownMenu from "components/DropdownMenu.svelte";
    import Item from "components/Item.svelte";

    import HandleSelection from "./HandleSelection.svelte";
    import HandleBackground from "./HandleBackground.svelte";
    import HandleControl from "./HandleControl.svelte";
    import MathjaxHandleInlineBlock from "./MathjaxHandleInlineBlock.svelte";
    import MathjaxHandleEditor from "./MathjaxHandleEditor.svelte";

    import { onDestroy } from "svelte";

    export let container: HTMLElement;

    let activeImage: HTMLImageElement | null = null;

    function resetHandle(): void {
        activeImage = null;
    }

    function maybeShowHandle(event: Event): void {
        if (event.target instanceof HTMLImageElement) {
            const image = event.target;
            resetHandle();

            if (image.dataset.anki === "mathjax") {
                activeImage = image;
            }
        }
    }

    container.addEventListener("click", maybeShowHandle);

    let dropdownApi: any;
    let removeEventListener: () => void = () => {
        /* noop */
    };

    function onImageResize(): void {
        if (activeImage) {
            errorMessage = activeImage.title;
            updateSelection().then(() => dropdownApi.update());
        }
    }

    $: if (activeImage) {
        activeImage.addEventListener("resize", onImageResize);

        const lastImage = activeImage;
        removeEventListener = () =>
            lastImage.removeEventListener("resize", onImageResize);
    } else {
        removeEventListener();
    }

    const resizeObserver = new ResizeObserver(async () => {
        if (activeImage) {
            await updateSelection();
            dropdownApi.update();
        }
    });

    resizeObserver.observe(container);

    let updateSelection: () => Promise<void>;
    let errorMessage: string;

    function getComponent(image: HTMLImageElement): HTMLElement {
        return image.closest("anki-mathjax")! as HTMLElement;
    }

    function onEditorUpdate(event: CustomEvent): void {
        /* this updates the image in Mathjax.svelte */
        getComponent(activeImage!).dataset.mathjax = event.detail.mathjax;
    }

    onDestroy(() => {
        container.removeEventListener("click", maybeShowHandle);
    });
</script>

<WithDropdown
    drop="down"
    autoOpen={true}
    autoClose={false}
    distance={4}
    let:createDropdown
>
    {#if activeImage}
        <HandleSelection
            image={activeImage}
            {container}
            bind:updateSelection
            on:mount={(event) => (dropdownApi = createDropdown(event.detail.selection))}
        >
            <HandleBackground tooltip={errorMessage} />

            <HandleControl offsetX={1} offsetY={1} />
        </HandleSelection>

        <DropdownMenu>
            <MathjaxHandleEditor
                initialValue={getComponent(activeImage).dataset.mathjax ?? ""}
                on:update={onEditorUpdate}
            />
            <div class="margin-x">
                <ButtonToolbar>
                    <Item>
                        <MathjaxHandleInlineBlock
                            {activeImage}
                            on:click={updateSelection}
                        />
                    </Item>
                </ButtonToolbar>
            </div>
        </DropdownMenu>
    {/if}
</WithDropdown>

<style lang="scss">
    .margin-x {
        margin: 0 0.125rem;
    }
</style>
