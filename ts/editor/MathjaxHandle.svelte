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
    let title: string;

    function onUpdate(event: CustomEvent) {
        activeImage!.parentElement!.dataset.mathjax = event.detail.mathjax;
        setTimeout(() => {
            updateSelection();
            title = activeImage!.title;
        });
    }
</script>

<WithDropdown
    placement="bottom"
    autoOpen={true}
    autoClose={false}
    let:createDropdown
    let:dropdownObject
>
    {#if activeImage}
        <HandleSelection
            image={activeImage}
            {container}
            offsetX={2}
            offsetY={2}
            bind:updateSelection
            on:mount={(event) => createDropdown(event.detail.selection)}
        >
            <HandleBackground {title} />

            <HandleControl offsetX={1} offsetY={1} />
        </HandleSelection>

        <DropdownMenu>
            <MathjaxHandleEditor
                initialValue={activeImage.parentElement?.dataset.mathjax ?? ""}
                on:update={(event) => {
                    onUpdate(event);
                    setTimeout(dropdownObject.update);
                }}
            />
            <div class="margin-x">
                <ButtonToolbar>
                    <Item>
                        <MathjaxHandleInlineBlock
                            {activeImage}
                            {isRtl}
                            on:click={async () => {
                                await tick();
                                updateSelection();
                                dropdownObject.update();
                            }}
                        />
                    </Item>
                </ButtonToolbar>
                <div />
            </div></DropdownMenu
        >
    {/if}
</WithDropdown>

<style lang="scss">
    .margin-x {
        margin: 0 0.125rem;
    }
</style>
