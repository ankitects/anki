<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getRemoveFormat } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { eraserIcon } from "./icons";

    const { focusedInput } = noteEditorContext.get();
    const { removeFormats } = editorToolbarContext.get();

    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: removeFormat = disabled ? null : getRemoveFormat(input);

    function remove(): void {
        removeFormat?.removeFormat(removeFormats);
    }

    const keyCombination = "Control+R";
</script>

<IconButton
    tooltip="{tr.editingRemoveFormatting()} ({getPlatformString(keyCombination)})"
    {disabled}
    on:click={remove}
>
    {@html eraserIcon}
</IconButton>

<Shortcut {keyCombination} on:action={remove} />
