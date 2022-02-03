<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import CommandIconButton from "./CommandIconButton.svelte";
    import BoldButton from "./BoldButton.svelte";
    import ItalicButton from "./ItalicButton.svelte";
    import UnderlineButton from "./UnderlineButton.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import ButtonGroupItem, {
        createProps,
        updatePropsList,
        setSlotHostContext,
    } from "../../components/ButtonGroupItem.svelte";

    import * as tr from "../../lib/ftl";
    import { superscriptIcon, subscriptIcon, eraserIcon } from "./icons";

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

        <ButtonGroupItem>
            <CommandIconButton
                key="superscript"
                shortcut="Control+="
                tooltip={tr.editingSuperscript()}
                >{@html superscriptIcon}</CommandIconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <CommandIconButton
                key="subscript"
                shortcut="Control+Shift+="
                tooltip={tr.editingSubscript()}>{@html subscriptIcon}</CommandIconButton
            >
        </ButtonGroupItem>

        <ButtonGroupItem>
            <CommandIconButton
                key="removeFormat"
                shortcut="Control+R"
                tooltip={tr.editingRemoveFormatting()}
                withoutState>{@html eraserIcon}</CommandIconButton
            >
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
