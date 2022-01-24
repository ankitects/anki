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
    import { getNoteEditor } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { boldIcon } from "./icons";

    function matchBold(element: Element): Exclude<MatchResult, MatchResult.ALONG> {
        if (!(element instanceof HTMLElement) && !(element instanceof SVGElement)) {
            return MatchResult.NO_MATCH;
        }

        if (element.tagName === "B" || element.tagName === "STRONG") {
            return MatchResult.MATCH;
        }

        const fontWeight = element.style.fontWeight;
        if (fontWeight === "bold" || Number(fontWeight) >= 400) {
            return MatchResult.KEEP;
        }

        return MatchResult.NO_MATCH;
    }

    function clearBold(element: Element): boolean {
        const htmlElement = element as HTMLElement | SVGElement;
        htmlElement.style.removeProperty("font-weight");

        if (htmlElement.style.cssText.length === 0) {
            htmlElement.removeAttribute("style");
        }

        return !htmlElement.hasAttribute("style") && element.className.length === 0;
    }

    const { activeInput } = getNoteEditor();

    $: input = $activeInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($activeInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? Promise.resolve(false) : surrounder!.isSurrounded(matchBold);
    }

    const element = document.createElement("strong");
    function makeBold(): void {
        surrounder?.surroundCommand(element, matchBold, clearBold);
    }

    const keyCombination = "Control+B";
</script>

<WithState
    key="bold"
    update={updateStateFromActiveInput}
    let:state={isBold}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingBoldText()} ({getPlatformString(keyCombination)})"
        active={isBold}
        {disabled}
        on:click={(event) => {
            makeBold();
            updateState(event);
        }}
    >
        {@html boldIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeBold();
            updateState(event);
        }}
    />
</WithState>
