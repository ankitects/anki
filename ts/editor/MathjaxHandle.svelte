<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { tick } from "svelte";

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

    const resizeObserver = new ResizeObserver(() => {
        if (activeImage) {
            updateSelection();
        }
    });

    resizeObserver.observe(container);

    let updateSelection: () => void;
    let dropdownApi: any;
    let title: string;

    function getComponent(image: HTMLImageElement): HTMLElement {
        return image.closest("anki-mathjax")! as HTMLElement;
    }

    function scheduleDropdownUpdate() {
        setTimeout(async () => {
            await tick();
            dropdownApi.update();
        });
    }

    async function onEditorUpdate(event: CustomEvent) {
        getComponent(activeImage!).dataset.mathjax = event.detail.mathjax;

        setTimeout(() => {
            updateSelection();
            title = activeImage!.title;
            scheduleDropdownUpdate();
        });
    }
</script>

<WithDropdown drop="down" autoOpen={true} autoClose={false} let:createDropdown>
    {#if activeImage}
        <HandleSelection
            image={activeImage}
            {container}
            offsetX={2}
            offsetY={2}
            bind:updateSelection
            on:mount={(event) => (dropdownApi = createDropdown(event.detail.selection))}
        >
            <HandleBackground {title} />

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
                            {isRtl}
                            on:click={() => {
                                updateSelection();
                                scheduleDropdownUpdate();
                            }}
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
