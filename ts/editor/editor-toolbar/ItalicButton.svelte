<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import type { MatchType } from "../../domlib/surround";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import { editingInputIsRichText } from "../rich-text-input";
    import { removeEmptyStyle, Surrounder } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { italicIcon } from "./icons";

    const surroundElement = document.createElement("em");

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "I" || element.tagName === "EM") {
            return match.remove();
        }

        if (["italic", "oblique"].includes(element.style.fontStyle)) {
            return match.clear((): void => {
                element.style.removeProperty("font-style");

                if (removeEmptyStyle(element) && element.className.length === 0) {
                    return match.remove();
                }
            });
        }
    }

    const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        name: tr.editingItalicText(),
        show: true,
        active: true,
        format,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    const { focusedInput } = noteEditorContext.get();
    const surrounder = Surrounder.make();
    let disabled: boolean;

    $: if (editingInputIsRichText($focusedInput)) {
        surrounder.richText = $focusedInput;
        disabled = false;
    } else {
        surrounder.disable();
        disabled = true;
    }

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? Promise.resolve(false) : surrounder!.isSurrounded(format);
    }

    function makeItalic(): void {
        surrounder.surround(format);
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
