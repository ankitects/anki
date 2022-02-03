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
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import ClozeButton from "./ClozeButton.svelte";
    import { micIcon, paperclipIcon } from "./icons";
    import LatexButton from "./LatexButton.svelte";

    const { focusedInput } = context.get();

    const attachmentKeyCombination = "F7";
    function onAttachment(): void {
        bridgeCommand("attach");
    }

    const recordKeyCombination = "F8";
    function onRecord(): void {
        bridgeCommand("record");
    }

    $: disabled = !editingInputIsRichText($focusedInput);

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
                tooltip="{tr.editingAttachPicturesaudiovideo()} ({getPlatformString(
                    attachmentKeyCombination,
                )})"
                iconSize={70}
                {disabled}
                on:click={onAttachment}
            >
                {@html paperclipIcon}
            </IconButton>
            <Shortcut
                keyCombination={attachmentKeyCombination}
                on:action={onAttachment}
            />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <IconButton
                tooltip="{tr.editingRecordAudio()} ({getPlatformString(
                    recordKeyCombination,
                )})"
                iconSize={70}
                {disabled}
                on:click={onRecord}
            >
                {@html micIcon}
            </IconButton>
            <Shortcut keyCombination={recordKeyCombination} on:action={onRecord} />
        </ButtonGroupItem>

        <ButtonGroupItem id="cloze">
            <ClozeButton />
        </ButtonGroupItem>

        <ButtonGroupItem>
            <LatexButton />
        </ButtonGroupItem>
    </DynamicallySlottable>
</ButtonGroup>
