<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { removeStyleProperties } from "@tslib/styling";
    import { singleCallback } from "@tslib/typing";
    import { onMount } from "svelte";

    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import type { MatchType } from "../../domlib/surround";
    import { surrounder } from "../rich-text-input";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { italicIcon } from "./icons";

    const surroundElement = document.createElement("i");

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "I" || element.tagName === "EM") {
            return match.remove();
        }

        if (["italic", "oblique"].includes(element.style.fontStyle)) {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "font-style") &&
                    element.className.length === 0
                ) {
                    return match.remove();
                }
            });
        }
    }

    const key = "italic";

    const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        key,
        name: tr.editingItalicText(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function makeItalic(): void {
        surrounder.surround(key);
    }

    const keyCombination = "Control+I";

    let disabled: boolean;

    onMount(() =>
        singleCallback(
            surrounder.active.subscribe((value) => (disabled = !value)),
            surrounder.registerFormat(key, format),
        ),
    );
</script>

<WithState {key} update={updateStateFromActiveInput} let:state={active} let:updateState>
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
