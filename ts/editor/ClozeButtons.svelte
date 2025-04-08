<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@generated/ftl";
    import { isApplePlatform } from "@tslib/platform";
    import { getPlatformString } from "@tslib/shortcuts";
    import { createEventDispatcher } from "svelte";
    import { get } from "svelte/store";

    import ButtonGroup from "$lib/components/ButtonGroup.svelte";
    import Icon from "$lib/components/Icon.svelte";
    import IconButton from "$lib/components/IconButton.svelte";
    import { clozeIcon, incrementClozeIcon } from "$lib/components/icons";
    import Shortcut from "$lib/components/Shortcut.svelte";

    import { context as noteEditorContext } from "./NoteEditor.svelte";
    import { editingInputIsRichText } from "./rich-text-input";

    export let alwaysEnabled = false;

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

    const dispatch = createEventDispatcher();

    async function onIncrementCloze(): Promise<void> {
        const highestCloze = getCurrentHighestCloze(true);

        dispatch("surround", {
            prefix: `{{c${highestCloze}::`,
            suffix: "}}",
        });
    }

    async function onSameCloze(): Promise<void> {
        const highestCloze = getCurrentHighestCloze(false);

        dispatch("surround", {
            prefix: `{{c${highestCloze}::`,
            suffix: "}}",
        });
    }

    $: enabled =
        alwaysEnabled || ($focusedInput && editingInputIsRichText($focusedInput));
    $: disabled = !enabled;

    const incrementKeyCombination = "Control+Shift+C";
    const sameKeyCombination = "Control+Alt+Shift+C";
</script>

<ButtonGroup>
    <IconButton
        tooltip="{tr.editingClozeDeletion()} ({getPlatformString(
            incrementKeyCombination,
        )})"
        {disabled}
        on:click={onIncrementCloze}
        --border-left-radius="5px"
    >
        <Icon icon={incrementClozeIcon} />
    </IconButton>

    <Shortcut
        keyCombination={incrementKeyCombination}
        event="keydown"
        on:action={onIncrementCloze}
    />

    <IconButton
        tooltip="{tr.editingClozeDeletionRepeat()} ({getPlatformString(
            sameKeyCombination,
        )})"
        {disabled}
        on:click={onSameCloze}
        --border-right-radius="5px"
    >
        <Icon icon={clozeIcon} />
    </IconButton>

    <Shortcut keyCombination={sameKeyCombination} {event} on:action={onSameCloze} />
</ButtonGroup>
