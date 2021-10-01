<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "../../components/WithDropdown.svelte";
    import ButtonToolbar from "../../components/ButtonToolbar.svelte";
    import DropdownMenu from "../../components/DropdownMenu.svelte";
    import Item from "../../components/Item.svelte";

    import HandleSelection from "../HandleSelection.svelte";
    import HandleBackground from "../HandleBackground.svelte";
    import HandleControl from "../HandleControl.svelte";

    import InlineBlock from "./InlineBlock.svelte";
    import Editor from "./Editor.svelte";

    import { onDestroy, tick } from "svelte";

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

            if (image.dataset.anki === "mathjax") {
                activeImage = image;
            }
        }
    }

    container.addEventListener("click", maybeShowHandle);
    container.addEventListener("key", resetHandle);
    container.addEventListener("paste", resetHandle);

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

    import { signifyCustomInput } from "../../editable/editable";

    function onEditorUpdate(event: CustomEvent): void {
        /* this updates the image in Mathjax.svelte */
        getComponent(activeImage!).dataset.mathjax = event.detail.mathjax;
        signifyCustomInput(activeImage!);
    }

    onDestroy(() => {
        container.removeEventListener("click", maybeShowHandle);
        container.removeEventListener("key", resetHandle);
        container.removeEventListener("paste", resetHandle);
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
            <Editor
                initialValue={getComponent(activeImage).dataset.mathjax ?? ""}
                on:update={onEditorUpdate}
                on:codemirrorblur={resetHandle}
            />
            <div class="margin-x">
                <ButtonToolbar>
                    <Item>
                        <InlineBlock {activeImage} on:click={updateSelection} />
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
