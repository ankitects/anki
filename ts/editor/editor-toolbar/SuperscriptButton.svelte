<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Match } from "../../domlib/surround";
    import { MatchType } from "../../domlib/surround";
    import { removeEmptyStyle } from "../surround";

    const surroundElement = document.createElement("sup");

    export function matcher(element: HTMLElement | SVGElement): Match {
        if (element.tagName === "SUP") {
            return { type: MatchType.MATCH };
        }

        if (element.style.verticalAlign === "super") {
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
    import { superscriptIcon } from "./icons";
    import { format as subscript } from "./SubscriptButton.svelte";

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.push(format);

    const { focusedInput } = noteEditorContext.get();
    $: input = $focusedInput as RichTextInputAPI;
    $: disabled = !editingInputIsRichText($focusedInput);
    $: surrounder = disabled ? null : getSurrounder(input);

    function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? Promise.resolve(false) : surrounder!.isSurrounded(matcher);
    }

    function makeSuper(): void {
        surrounder?.surroundCommand(format, [subscript]);
    }

    const keyCombination = "Control+=";
</script>

<WithState
    key="super"
    update={updateStateFromActiveInput}
    let:state={active}
    let:updateState
>
    <IconButton
        tooltip="{tr.editingSuperscript()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeSuper();
            updateState(event);
            updateStateByKey("sub", event);
        }}
    >
        {@html superscriptIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeSuper();
            updateState(event);
            updateStateByKey("sub", event);
        }}
    />
</WithState>
