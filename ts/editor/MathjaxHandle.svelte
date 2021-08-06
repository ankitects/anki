<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import WithDropdown from "components/WithDropdown.svelte";
    import ButtonDropdown from "components/ButtonDropdown.svelte";
    import Item from "components/Item.svelte";

    import HandleSelection from "./HandleSelection.svelte";
    import HandleBackground from "./HandleBackground.svelte";
    import HandleControl from "./HandleControl.svelte";
    import MathjaxHandleInlineBlock from "./MathjaxHandleInlineBlock.svelte";

    export let activeImage: HTMLImageElement | null = null;
    export let container: HTMLElement;
    export let isRtl: boolean;

    let updateSelection: () => void;
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
            <HandleBackground on:click={(event) => event.stopPropagation()} />

            <HandleControl offsetX={1} offsetY={1} />
        </HandleSelection>

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
</WithDropdown>
