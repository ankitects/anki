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
    import { superscriptIcon } from "./icons";

    const surroundElement = document.createElement("sup");

    export function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "SUP") {
            return match.remove();
        }

        if (element.style.verticalAlign === "super") {
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

    const key = "superscript";

    export const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        key,
        name: tr.editingSuperscript(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function makeSuper(): void {
        surrounder.surround(key, ["subscript"]);
    }

    const keyCombination = "Control+=";

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
        tooltip="{tr.editingSuperscript()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeSuper();
            updateState(event);
            updateStateByKey("subscript", event);
        }}
    >
        {@html superscriptIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeSuper();
            updateState(event);
            updateStateByKey("subscript", event);
        }}
    />
</WithState>
