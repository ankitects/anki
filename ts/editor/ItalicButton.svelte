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
    import { italicIcon } from "./icons";
    import { getNoteEditor } from "./OldEditorAdapter.svelte";
    import type { RichTextInputAPI } from "./RichTextInput.svelte";

    function matchItalic(element: Element): MatchResult {
        if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
            return MatchResult.NO_MATCH;
        }

        if (element.tagName === "I" || element.tagName === "EM") {
            return MatchResult.MATCH;
        }

        if (["italic", "oblique"].includes(element.style.fontStyle)) {
            return MatchResult.KEEP;
        }

        return MatchResult.NO_MATCH;
    }

    function clearItalic(element: Element): boolean {
        const htmlElement = element as HTMLElement | SVGElement;
        htmlElement.style.removeProperty("font-style");

        if (htmlElement.style.cssText.length === 0) {
            htmlElement.removeAttribute("style");
        }

        return !htmlElement.hasAttribute("style") && element.className.length === 0;
    }

    const { focusInRichText, activeInput } = getNoteEditor();

    $: input = $activeInput;
    $: disabled = !$focusInRichText;

    function updateStateFromActiveInput(): Promise<boolean> {
        return !input || input.name === "plain-text"
            ? Promise.resolve(false)
            : isSurrounded(input, matchItalic);
    }

    function makeItalic(): void {
        surroundCommand(
            input as RichTextInputAPI,
            document.createElement("em"),
            matchItalic,
            clearItalic,
        );
    }

    const keyCombination = "Control+I";
</script>

<WithState
    key="italic"
    update={updateStateFromActiveInput}
    let:state={active}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingItalicText()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeItalic();
            updateState(event);
        }}
    >
        {@html italicIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeItalic();
            updateState(event);
        }}
    />
</WithState>
