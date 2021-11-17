<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import IconButton from "../components/IconButton.svelte";
    import Shortcut from "../components/Shortcut.svelte";
    import WithState from "../components/WithState.svelte";
    import { MatchResult } from "../domlib/surround";
    import { getPlatformString } from "../lib/shortcuts";
    import { isSurrounded, surroundCommand } from "./surround";
    import { underlineIcon } from "./icons";
    import { getNoteEditor } from "./OldEditorAdapter.svelte";
    import type { RichTextInputAPI } from "./RichTextInput.svelte";

    function matchUnderline(element: Element): MatchResult {
        if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
            return MatchResult.NO_MATCH;
        }

        if (element.tagName === "U") {
            return MatchResult.MATCH;
        }

        return MatchResult.NO_MATCH;
    }

    const { focusInRichText, activeInput } = getNoteEditor();

    $: input = $activeInput;
    $: disabled = !$focusInRichText;

    function updateStateFromActiveInput(): Promise<boolean> {
        return !input || input.name === "plain-text"
            ? Promise.resolve(false)
            : isSurrounded(input, matchUnderline);
    }

    function makeUnderline(): void {
        surroundCommand(
            input as RichTextInputAPI,
            document.createElement("u"),
            matchUnderline,
        );
    }

    const keyCombination = "Control+U";
</script>

<WithState
    key="underline"
    update={updateStateFromActiveInput}
    let:state={active}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingUnderlineText()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeUnderline();
            updateState(event);
        }}
    >
        {@html underlineIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeUnderline();
            updateState(event);
        }}
    />
</WithState>
