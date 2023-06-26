<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "components/ButtonGroup.svelte";
    import DynamicallySlottable from "components/DynamicallySlottable.svelte";
    import IconButton from "components/IconButton.svelte";
    import { ioImageLoaded } from "image-occlusion/store";

    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { mdiRefresh, mdiViewDashboard } from "./icons";

    export let api = {};

    // reset for new occlusion in add mode
    const resetIOImageLoaded = () => {
        ioImageLoaded.set(false);
        buttonPressed = false;
        globalThis.canvas.clear();
    };
    globalThis.resetIOImageLoaded = resetIOImageLoaded;

    // in mask editor so button pressed by default
    let buttonPressed = true;
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
                    if (buttonPressed) {
                        bridgeCommand(`toggleMaskEditor:${false}`);
                        buttonPressed = false;
                    } else {
                        bridgeCommand(`toggleMaskEditor:${true}`);
                        buttonPressed = true;
                    }
                }}
            >
                {@html mdiViewDashboard}
            </IconButton>
        </ButtonGroupItem>

        {#if buttonPressed}
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
