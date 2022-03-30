<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { get } from "svelte/store";

    import ButtonGroup from "../../components/ButtonGroup.svelte";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import * as tr from "../../lib/ftl";
    import { isApplePlatform } from "../../lib/platform";
    import { getPlatformString } from "../../lib/shortcuts";
    import { wrapInternal } from "../../lib/wrap";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { clozeIcon, incrementClozeIcon } from "./icons";

    const { focusedInput, fields } = noteEditorContext.get();

    // Workaround for Cmd+Option+Shift+C not working on macOS. The keyup approach works
    // on Linux as well, but fails on Windows.
    const event = isApplePlatform() ? "keyup" : "keydown";

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

    async function onIncrementCloze(): Promise<void> {
        const richText = await richTextAPI.element;

        const highestCloze = getCurrentHighestCloze(true);
        wrapInternal(richText, `{{c${highestCloze}::`, "}}", false);
    }

    async function onSameCloze(): Promise<void> {
        const richText = await richTextAPI.element;

        const highestCloze = getCurrentHighestCloze(false);
        wrapInternal(richText, `{{c${highestCloze}::`, "}}", false);
    }

    $: disabled = !editingInputIsRichText($focusedInput);

    const incrementKeyCombination = "Control+Shift+C";
    const sameKeyCombination = "Control+Alt+Shift+C";
</script>

<ButtonGroup>
    <IconButton
        tooltip="{tr.editingClozeDeletion()} {getPlatformString(
            incrementKeyCombination,
        )}"
        {disabled}
        on:click={onIncrementCloze}
        --border-left-radius="5px"
    >
        {@html incrementClozeIcon}
    </IconButton>

    <Shortcut
        keyCombination={incrementKeyCombination}
        {event}
        on:action={onIncrementCloze}
    />

    <IconButton
        tooltip="{tr.editingClozeDeletion()} {getPlatformString(sameKeyCombination)}"
        {disabled}
        on:click={onSameCloze}
        --border-right-radius="5px"
    >
        {@html clozeIcon}
    </IconButton>

    <Shortcut keyCombination={sameKeyCombination} on:action={onSameCloze} />
</ButtonGroup>
