<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { get } from "svelte/store";

    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { wrapInternal } from "../../lib/wrap";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { ellipseIcon } from "./icons";

    const { focusedInput, fields } = noteEditorContext.get();

    const clozePattern = /\{\{c(\d+)::/gu;
    function getCurrentHighestCloze(increment: boolean): number {
        let highest = 0;

        for (const field of fields) {
            const content = field.editingArea?.content;
            const fieldHTML = content ? get(content) : "";

            const matches: number[] = [];
            let match: RegExpMatchArray | null = null;

            while ((match = clozePattern.exec(fieldHTML))) {
                matches.push(Number(match[1]));
            }

            highest = Math.max(highest, ...matches);
        }

        if (increment) {
            highest++;
        }

        return Math.max(1, highest);
    }

    $: richTextAPI = $focusedInput as RichTextInputAPI;

    async function onCloze(event: KeyboardEvent | MouseEvent): Promise<void> {
        const highestCloze = getCurrentHighestCloze(!event.getModifierState("Alt"));
        const richText = await richTextAPI.element;
        wrapInternal(richText, `{{c${highestCloze}::`, "}}", false);
    }

    $: disabled = !editingInputIsRichText($focusedInput);

    const keyCombination = "Control+Alt?+Shift+C";
</script>

<IconButton
    tooltip="{tr.editingClozeDeletion()} {getPlatformString(keyCombination)}"
    {disabled}
    on:click={onCloze}
>
    {@html ellipseIcon}
</IconButton>

<Shortcut {keyCombination} on:action={(event) => onCloze(event.detail.originalEvent)} />
