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

    import { onMount, tick } from "svelte";
    import { getRichTextInput } from "../RichTextInput.svelte";
    import type { RichTextInputContextAPI } from "../RichTextInput.svelte";
    import { noop } from "../../lib/functional";

    const { container, api } = getRichTextInput() as RichTextInputContextAPI;

    let activeImage: HTMLImageElement | null = null;
    let allow: () => void;

    function showHandle(image: HTMLImageElement): void {
        allow = api.preventResubscription();
        activeImage = image;
    }

    async function maybeShowHandle(event: Event): Promise<void> {
        await resetHandle();
        const target = event.target;

        if (target instanceof HTMLImageElement && target.dataset.anki === "mathjax") {
            showHandle(target);
        }
    }

    async function showAutofocusHandle({
        detail,
    }: CustomEvent<HTMLImageElement>): Promise<void> {
        await resetHandle();
        showHandle(detail);
    }

    async function resetHandle(): Promise<void> {
        if (activeImage) {
            allow();
            activeImage = null;
            await tick();
        }
    }

    onMount(() => {
        container.addEventListener("click", maybeShowHandle);
        container.addEventListener("focusmathjax" as any, showAutofocusHandle);
        container.addEventListener("key", resetHandle);
        container.addEventListener("paste", resetHandle);

        return () => {
            container.removeEventListener("click", maybeShowHandle);
            container.removeEventListener("focusmathjax" as any, showAutofocusHandle);
            container.removeEventListener("key", resetHandle);
            container.removeEventListener("paste", resetHandle);
        };
    });

    let dropdownApi: any;
    async function onImageResize(): Promise<void> {
        if (activeImage) {
            errorMessage = activeImage.title;
            await updateSelection();
            dropdownApi.update();
        }
    }

    let clearResize = noop;
    function handleImageResizing(activeImage: HTMLImageElement | null) {
        if (activeImage) {
            activeImage.addEventListener("resize", onImageResize);

            const lastImage = activeImage;
            clearResize = () => lastImage.removeEventListener("resize", onImageResize);
        } else {
            clearResize();
        }
    }

    $: handleImageResizing(activeImage);

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
