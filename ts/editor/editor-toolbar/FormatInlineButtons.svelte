<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem, {
        createProps,
        setSlotHostContext,
        updatePropsList,
    } from "../../components/ButtonGroupItem.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import BoldButton from "./BoldButton.svelte";
    import ItalicButton from "./ItalicButton.svelte";
    import UnderlineButton from "./UnderlineButton.svelte";
    import SuperscriptButton from "./SuperscriptButton.svelte";
    import SubscriptButton from "./SubscriptButton.svelte";

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
            <BoldButton />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <ItalicButton />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <UnderlineButton />
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>

<ButtonGroup>
    <DynamicallySlottable
        slotHost={ButtonGroupItem}
        {createProps}
        {updatePropsList}
        {setSlotHostContext}
        {api}
    >
        <ButtonGroupItem>
            <SuperscriptButton />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <SubscriptButton />
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
