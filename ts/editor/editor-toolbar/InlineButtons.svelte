<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import DynamicallySlottable from "$lib/components/DynamicallySlottable.svelte";
    import Item from "$lib/components/Item.svelte";

    import BoldButton from "./BoldButton.svelte";
    import HighlightColorButton from "./HighlightColorButton.svelte";
    import ItalicButton from "./ItalicButton.svelte";
    import RemoveFormatButton from "./RemoveFormatButton.svelte";
    import StrikethroughButton from "./StrikethroughButton.svelte";
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

<DynamicallySlottable slotHost={Item} {api}>
    <Item>
        <ButtonGroup>
            <BoldButton --border-left-radius="5px" />
            <ItalicButton />
            <UnderlineButton />
            <StrikethroughButton --border-right-radius="5px" />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup>
            <SuperscriptButton --border-left-radius="5px" />
            <SubscriptButton --border-right-radius="5px" />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup>
            <TextColorButton color={textColor} />
            <HighlightColorButton color={highlightColor} />
        </ButtonGroup>
    </Item>

    <Item>
        <ButtonGroup>
            <RemoveFormatButton />
        </ButtonGroup>
    </Item>
</DynamicallySlottable>
