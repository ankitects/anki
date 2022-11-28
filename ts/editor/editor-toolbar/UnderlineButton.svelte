<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { getPlatformString } from "@tslib/shortcuts";
    import { singleCallback } from "@tslib/typing";
    import { onMount } from "svelte";

    import IconButton from "../../components/IconButton.svelte";
    import Shortcut from "../../components/Shortcut.svelte";
    import WithState from "../../components/WithState.svelte";
    import type { MatchType } from "../../domlib/surround";
    import { surrounder } from "../rich-text-input";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";
    import { underlineIcon } from "./icons";

    const surroundElement = document.createElement("u");

    function matcher(element: HTMLElement | SVGElement, match: MatchType): void {
        if (element.tagName === "U") {
            return match.remove();
        }
    }

    function clearer() {
        return false;
    }

    const key = "underline";

    const format = {
        surroundElement,
        matcher,
        clearer,
    };

    const namedFormat = {
        key,
        name: tr.editingUnderlineText(),
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function makeUnderline(): void {
        surrounder.surround(key);
    }

    const keyCombination = "Control+U";

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
        tooltip="{tr.editingUnderlineText()} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            makeUnderline();
            updateState(event);
        }}
    >
        {@html underlineIcon}
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            makeUnderline();
            updateState(event);
        }}
    />
</WithState>
