<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../../lib/ftl";
    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import ButtonGroupItem from "../../components/ButtonGroupItem.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import ClozeButton from "./ClozeButton.svelte";
    import LatexButton from "./LatexButton.svelte";
    import { bridgeCommand } from "../../lib/bridgecommand";
    import { getPlatformString } from "../../lib/shortcuts";
    import { getNoteEditor } from "../OldEditorAdapter.svelte";
    import { paperclipIcon, micIcon } from "./icons";

    export let api = {};
    const { focusInRichText } = getNoteEditor();

    const attachmentKeyCombination = "F3";
    function onAttachment(): void {
        bridgeCommand("attach");
    }

    const recordKeyCombination = "F5";
    function onRecord(): void {
        bridgeCommand("record");
    }

    $: disabled = !$focusInRichText;
</script>

<ButtonGroup {api}>
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
        <Shortcut keyCombination={attachmentKeyCombination} on:action={onAttachment} />
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
</ButtonGroup>
