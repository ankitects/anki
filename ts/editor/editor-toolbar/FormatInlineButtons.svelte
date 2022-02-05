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
    import HighlightColorButton from "./HighlightColorButton.svelte";
    import ItalicButton from "./ItalicButton.svelte";
    import SubscriptButton from "./SubscriptButton.svelte";
    import SuperscriptButton from "./SuperscriptButton.svelte";
    import TextColorButton from "./TextColorButton.svelte";
    import UnderlineButton from "./UnderlineButton.svelte";

    export let api = {};

    let textColor: string = "black";
    let highlightColor: string = "black";
    export function setColorButtons([textClr, highlightClr]: [string, string]): void {
        textColor = textClr;
        highlightColor = highlightClr;
    }

    Object.assign(globalThis, { setColorButtons });
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

<ButtonGroup>
    <TextColorButton color={textColor} />
    <HighlightColorButton color={highlightColor} />
</ButtonGroup>
