<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Match } from "../../domlib/surround";
    import { MatchType } from "../../domlib/surround";
    import { removeEmptyStyle } from "../surround";

    const surroundElement = document.createElement("sub");

    export function matcher(element: HTMLElement | SVGElement): Match {
        if (element.tagName === "SUB") {
            return { type: MatchType.MATCH };
        }

        if (element.style.verticalAlign === "sub") {
            return {
                type: MatchType.CLEAR,
                clear(element: HTMLElement | SVGElement): boolean {
                    element.style.removeProperty("vertical-align");
                    return removeEmptyStyle(element) && element.className.length === 0;
                },
            };
        }

        return { type: MatchType.NONE };
    }

    export const format = {
        surroundElement,
        matcher,
    };
</script>

<script lang="ts">
    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import { updateStateByKey } from "../../components/WithState.svelte";
    import * as tr from "../../lib/ftl";
    import { getPlatformString } from "../../lib/shortcuts";
    import { context as noteEditorContext } from "../NoteEditor.svelte";
    import type { RichTextInputAPI } from "../rich-text-input";
    import { editingInputIsRichText } from "../rich-text-input";
    import { getSurrounder } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { subscriptIcon } from "./icons";
    import { format as superscript } from "./SuperscriptButton.svelte";

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(format);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? Promise.resolve(false) : surrounder!.isSurrounded(matcher);
    }

    function makeSub(): void {
        surrounder?.surroundCommand(format, [superscript]);
    }

    const keyCombination = "Control+Shift+=";
</script>

<WithState
    key="sub"
    update={updateStateFromActiveInput}
    let:state={active}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingSubscript()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeSub();
            updateState(event);
            updateStateByKey("super", event);
        }}
    >
        {@html subscriptIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeSub();
            updateState(event);
            updateStateByKey("super", event);
        }}
    />
</WithState>
