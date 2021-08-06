<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "components/WithDropdown.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
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
    let edit = false;

    function onUpdate(event: CustomEvent) {
        activeImage!.parentElement!.dataset.mathjax = event.detail.mathjax;
        setTimeout(updateSelection);
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
            <HandleBackground
                on:click={(event) => event.stopPropagation()}
                on:dblclick={() => (edit = !edit)}
            />

            <HandleControl offsetX={1} offsetY={1} />
        </HandleSelection>

        {#if !edit}
            <DropdownMenu>
                <MathjaxHandleEditor
                    initialValue={activeImage.parentElement?.dataset.mathjax ?? ""}
                    on:update={(event) => {
                        onUpdate(event);
                        setTimeout(dropdownObject.update);
                    }}
                />
            </DropdownMenu>
        {:else}
            <ButtonDropdown>
                <div
                    on:click={() => {
                        updateSelection();
                        dropdownObject.update();
                    }}
                >
                    <Item>
                        <MathjaxHandleInlineBlock {activeImage} {isRtl} />
                    </Item>
                </div>
            </ButtonDropdown>
        {/if}
    {/if}
</WithDropdown>
