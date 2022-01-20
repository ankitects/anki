<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../../lib/ftl";
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import { MatchResult } from "../../domlib/surround";
    import { getPlatformString } from "../../lib/shortcuts";
    import { getSurrounder } from "../surround";
    import { getNoteEditor } from "../OldEditorAdapter.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { underlineIcon } from "./icons";

    function matchUnderline(element: Element): Exclude<MatchResult, MatchResult.ALONG> {
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
    $: surrounder = disabled ? null : getSurrounder(input as RichTextInputAPI);

    function updateStateFromActiveInput(): Promise<boolean> {
        return !input || input.name === "plain-text"
            ? Promise.resolve(false)
            : surrounder!.isSurrounded(matchUnderline);
    }

    const element = document.createElement("u");
    function makeUnderline(): void {
        surrounder!.surroundCommand(element, matchUnderline);
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
