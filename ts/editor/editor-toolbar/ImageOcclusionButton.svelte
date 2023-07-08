<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "components/ButtonGroup.svelte";
    import DynamicallySlottable from "components/DynamicallySlottable.svelte";
    import IconButton from "components/IconButton.svelte";
    import { maskEditorButtonPressed } from "image-occlusion/store";
    import { get } from "svelte/store";

    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { mdiRefresh, mdiViewDashboard } from "./icons";

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
                on:click={() => {
                    if (get(maskEditorButtonPressed)) {
                        bridgeCommand(`toggleMaskEditor:${false}`);
                    } else {
                        bridgeCommand(`toggleMaskEditor:${true}`);
                    }
                    maskEditorButtonPressed.set(!get(maskEditorButtonPressed));
                }}
            >
                {@html mdiViewDashboard}
            </IconButton>
        </ButtonGroupItem>

        {#if get(maskEditorButtonPressed)}
            <ButtonGroupItem>
                <IconButton
                    on:click={() => {
                        bridgeCommand("addImageForOcclusion");
                    }}
                >
                    {@html mdiRefresh}
                </IconButton>
            </ButtonGroupItem>
        {/if}
    </DynamicallySlottable>
</ButtonGroup>
