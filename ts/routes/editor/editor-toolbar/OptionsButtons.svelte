<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "$lib/components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";

    import OptionsButton from "./OptionsButton.svelte";

    export let api = {};
    export let isLegacy = false;
</script>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <ButtonGroupItem id="options">
            <OptionsButton {isLegacy} />
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
