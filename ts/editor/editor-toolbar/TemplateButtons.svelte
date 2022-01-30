<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import DynamicallySlottable from "../../components/DynamicallySlottable.svelte";
    import ButtonGroupItem, {
        createProps,
        updatePropsList,
        setSlotHostContext,
    } from "../../components/ButtonGroupItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import ClozeButton from "./ClozeButton.svelte";
    import LatexButton from "./LatexButton.svelte";

    import * as tr from "../../lib/ftl";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { paperclipIcon, micIcon } from "./icons";

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
