<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import { MatchResult } from "../../domlib/surround";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getSurrounder } from "../surround";
    import { italicIcon } from "./icons";

    function matchItalic(element: Element): Exclude<MatchResult, MatchResult.ALONG> {
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

    const { focusedInput } = noteEditorContext.get();

    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled
            ? Promise.resolve(false)
            : surrounder!.isSurrounded(matchItalic);
    }

    const element = document.createElement("em");
    function makeItalic(): void {
        surrounder!.surroundCommand(element, matchItalic, clearItalic);
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
