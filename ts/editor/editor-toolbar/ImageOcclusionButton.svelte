<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "$lib/components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import { mdiTableRefresh, mdiViewDashboard } from "$lib/components/icons";

    import {
        ioImageLoadedStore,
        ioMaskEditorVisible,
    } from "../../routes/image-occlusion/store";
    import { toggleMaskEditorKeyCombination } from "../../routes/image-occlusion/tools/shortcuts";

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
                tooltip="{tr.editingImageOcclusionToggleMaskEditor()} ({toggleMaskEditorKeyCombination})"
            >
                <Icon icon={mdiViewDashboard} />
            </IconButton>
            <Shortcut
                keyCombination={toggleMaskEditorKeyCombination}
                on:action={() => {
                    $ioMaskEditorVisible = !$ioMaskEditorVisible;
                }}
            />
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
                <Icon icon={mdiTableRefresh} />
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
