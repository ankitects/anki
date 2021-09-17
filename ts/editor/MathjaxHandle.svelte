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

    export let activeImage: HTMLImageElement | null = null;
    export let container: HTMLElement;
    export let isRtl: boolean;

    let dropdownApi: any;

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

    const onImageResize = (resolve: () => void) => (): void => {
        errorMessage = activeImage!.title;
        updateSelection().then(resolve);
    };

    function onEditorUpdate(event: CustomEvent): Promise<void> {
        let selectionResolve: (value: void) => void;
        const afterSelectionUpdate = new Promise((resolve: (value: void) => void) => {
            selectionResolve = resolve;
        });

        const imageResize = onImageResize(selectionResolve!);

        activeImage!.addEventListener("resize", imageResize, { once: true });
        /* this updates the image in Mathjax.svelte */
        getComponent(activeImage!).dataset.mathjax = event.detail.mathjax;

        return afterSelectionUpdate;
    }
</script>

<WithDropdown
    drop="down"
    autoOpen={true}
    autoClose={false}
    distance={4}
    let:createDropdown
    let:dropdownObject
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
                on:update={async (event) => {
                    await onEditorUpdate(event);
                    dropdownObject.update();
                }}
            />
            <div class="margin-x">
                <ButtonToolbar>
                    <Item>
                        <MathjaxHandleInlineBlock
                            {activeImage}
                            {isRtl}
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
