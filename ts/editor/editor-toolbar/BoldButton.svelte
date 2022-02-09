<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import type { Match } from "../../domlib/surround";
    import { MatchType } from "../../domlib/surround";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getSurrounder, removeEmptyStyle } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { boldIcon } from "./icons";

    const surroundElement = document.createElement("strong");

    function matcher(element: HTMLElement | SVGElement): Match {
        if (element.tagName === "B" || element.tagName === "STRONG") {
            return { type: MatchType.MATCH };
        }

        const fontWeight = element.style.fontWeight;
        if (fontWeight === "bold" || Number(fontWeight) >= 400) {
            return {
                type: MatchType.CLEAR,
                clear(element: HTMLElement | SVGElement): boolean {
                    element.style.removeProperty("font-weight");
                    return removeEmptyStyle(element) && element.className.length === 0;
                },
            };
        }

        return { type: MatchType.NONE };
    }

    const format = {
        surroundElement,
        matcher,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(format);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? Promise.resolve(false) : surrounder!.isSurrounded(matcher);
    }

    function makeBold(): void {
        surrounder?.surroundCommand(format);
    }

    const keyCombination = "Control+B";
</script>

<WithState
    key="bold"
    update={updateStateFromActiveInput}
    let:state={active}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingBoldText()} ({getPlatformString(keyCombination)})"
        {active}
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
