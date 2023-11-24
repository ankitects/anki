<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import ButtonGroup from "components/ButtonGroup.svelte";
    import DynamicallySlottable from "components/DynamicallySlottable.svelte";
    import IconButton from "components/IconButton.svelte";
    import { ioImageLoadedStore, ioMaskEditorVisible } from "image-occlusion/store";

    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import { mdiTableRefresh, mdiViewDashboard } from "./icons";

    export let api = {};
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
                    $ioMaskEditorVisible = !$ioMaskEditorVisible;
                }}
                tooltip={tr.editingImageOcclusionToggleMaskEditor()}
            >
                {@html mdiViewDashboard}
            </IconButton>
        </ButtonGroupItem>
        <ButtonGroupItem>
            <IconButton
                id="io-reset-btn"
                disabled={!$ioImageLoadedStore}
                on:click={() => {
                    if (confirm(tr.editingImageOcclusionConfirmReset())) {
                        globalThis.resetIOImageLoaded();
                    } else {
                        return;
                    }
                }}
                tooltip={tr.editingImageOcclusionReset()}
            >
                {@html mdiTableRefresh}
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
