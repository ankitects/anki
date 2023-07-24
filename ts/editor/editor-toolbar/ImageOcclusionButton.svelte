<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "components/ButtonGroup.svelte";
    import DynamicallySlottable from "components/DynamicallySlottable.svelte";
    import IconButton from "components/IconButton.svelte";
    import { ioMaskEditorVisibleStore } from "image-occlusion/store";

    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import { mdiViewDashboard } from "./icons";

    export let api = {};
    const ioMaskEditorVisible = ioMaskEditorVisibleStore;
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <ButtonGroupItem>
            <IconButton
                id="io-mask-btn"
                class={$ioMaskEditorVisible ? "active-io-btn" : ""}
                on:click={() => {
                    ioMaskEditorVisibleStore.set(!$ioMaskEditorVisible);
                }}
            >
                {@html mdiViewDashboard}
            </IconButton>
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>

<style>
    :global(.active-io-btn) {
        background: var(--button-primary-bg) !important;
        color: white !important;
    }
</style>
