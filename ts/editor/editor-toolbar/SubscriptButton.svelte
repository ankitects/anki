<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { MatchType } from "../../domlib/surround";
    import { removeEmptyStyle } from "../surround";

    const surroundElement = document.createElement("sub");

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUB") {
            return match.remove();
        }

        if (element.style.verticalAlign === "sub") {
            return match.clear((): void => {
                element.style.removeProperty("vertical-align");

                if (removeEmptyStyle(element) && element.className.length === 0) {
                    return match.remove();
                }
            });
        }
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
    import { editingInputIsRichText } from "../rich-text-input";
    import { Surrounder } from "../surround";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { subscriptIcon } from "./icons";
    import { format as superscript } from "./SuperscriptButton.svelte";

    const namedFormat = {
        name: tr.editingSubscript(),
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

    function makeSub(): void {
        surrounder.surround(format, [superscript]);
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
