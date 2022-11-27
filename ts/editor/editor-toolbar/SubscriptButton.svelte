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
    import { updateStateByKey } from "../../components/WithState.svelte";
    import type { MatchType } from "../../domlib/surround";
    import { surrounder } from "../rich-text-input";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { subscriptIcon } from "./icons";

    const surroundElement = document.createElement("sub");

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUB") {
            return match.remove();
        }

        if (element.style.verticalAlign === "sub") {
            return match.clear((): void => {
                if (
                    removeStyleProperties(element, "vertical-align") &&
                    element.className.length === 0
                ) {
                    return match.remove();
                }
            });
        }
    }

    const key = "subscript";

    const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        key,
        name: tr.editingSubscript(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function makeSub(): void {
        surrounder.surround(key, ["superscript"]);
    }

    const keyCombination = "Control+Shift+=";

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
        tooltip="{tr.editingSubscript()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeSub();
            updateState(event);
            updateStateByKey("superscript", event);
        }}
    >
        {@html subscriptIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeSub();
            updateState(event);
            updateStateByKey("superscript", event);
        }}
    />
</WithState>
