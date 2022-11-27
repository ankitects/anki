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
    import { boldIcon } from "./icons";

    const surroundElement = document.createElement("b");

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "B" || element.tagName === "STRONG") {
            return match.remove();
        }

        const fontWeight = element.style.fontWeight;
        if (fontWeight === "bold" || Number(fontWeight) >= 400) {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "font-weight") &&
                    element.className.length === 0
                ) {
                    match.remove();
                }
            });
        }
    }

    const key = "bold";

    const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        key,
        name: tr.editingBoldText(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function makeBold(): void {
        surrounder.surround(key);
    }

    const keyCombination = "Control+B";

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
