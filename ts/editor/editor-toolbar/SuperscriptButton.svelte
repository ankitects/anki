<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { MatchType } from "../../domlib/surround";
    import { removeEmptyStyle } from "../surround";

    const surroundElement = document.createElement("sup");

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUP") {
            return match.remove();
        }

        if (element.style.verticalAlign === "super") {
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
    import { superscriptIcon } from "./icons";
    import { format as subscript } from "./SubscriptButton.svelte";

    const namedFormat = {
        name: tr.editingSuperscript(),
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

    function makeSuper(): void {
        surrounder.surround(format, [subscript]);
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
