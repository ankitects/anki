<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import Item from "../../components/Item.svelte";
    import BoldButton from "./BoldButton.svelte";
    import HighlightColorButton from "./HighlightColorButton.svelte";
    import ItalicButton from "./ItalicButton.svelte";
    import RemoveFormatButton from "./RemoveFormatButton.svelte";
    import SubscriptButton from "./SubscriptButton.svelte";
    import SuperscriptButton from "./SuperscriptButton.svelte";
    import TextColorButton from "./TextColorButton.svelte";
    import UnderlineButton from "./UnderlineButton.svelte";
    import { surrounder } from "../rich-text-input";

    export let api = {};

    let textColor: string = "black";
    let highlightColor: string = "black";
    export function setColorButtons([textClr, highlightClr]: [string, string]): void {
        textColor = textClr;
        highlightColor = highlightClr;
    }

    let disabled: boolean;
    surrounder.active.subscribe((value) => (disabled = !value));
    $: console.log('disabled', disabled);

    Object.assign(globalThis, { setColorButtons });
</script>

<DynamicallySlottable slotHost={Item} {api}>
    <Item>
        <ButtonGroup {disabled}>
            <BoldButton {disabled} />
            <ItalicButton {disabled} />
            <UnderlineButton {disabled} />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup {disabled}>
            <SuperscriptButton {disabled} />
            <SubscriptButton {disabled} />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup {disabled}>
            <TextColorButton {disabled} color={textColor} />
            <HighlightColorButton {disabled} color={highlightColor} />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup {disabled}>
            <RemoveFormatButton {disabled} />
        </ButtonGroup>
    </Item>
</DynamicallySlottable>
