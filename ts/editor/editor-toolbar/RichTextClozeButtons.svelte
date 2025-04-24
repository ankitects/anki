<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { wrapInternal } from "@tslib/wrap";

    import ClozeButtons from "../ClozeButtons.svelte";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";

    const { focusedInput } = noteEditorContext.get();

    $: richTextAPI = $focusedInput as RichTextInputAPI;

    async function onSurround({ detail }): Promise<void> {
        if (!richTextAPI.isClozeField) {
            return;
        }
        const richText = await richTextAPI.element;
        const { prefix, suffix } = detail;

        wrapInternal(richText, prefix, suffix, false);
    }
</script>

<ClozeButtons on:surround={onSurround} />
