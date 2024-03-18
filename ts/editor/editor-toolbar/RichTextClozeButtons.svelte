<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { wrapClozeInternal } from "@tslib/wrap";

    import ClozeButtons from "../ClozeButtons.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";

    const { focusedInput } = noteEditorContext.get();

    $: richTextAPI = $focusedInput as RichTextInputAPI;

    async function onCloze({ detail }): Promise<void> {
        const richText = await richTextAPI.element;
        const { n } = detail;
        wrapClozeInternal(richText, n);
    }
</script>

<ClozeButtons on:cloze={onCloze} />
