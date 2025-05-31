<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { getPlatformString } from "@tslib/shortcuts";
    import { singleCallback } from "@tslib/typing";
    import { onMount } from "svelte";

    import IconButton from "$lib/components/IconButton.svelte";
    import Shortcut from "$lib/components/Shortcut.svelte";
    import WithState, { updateStateByKey } from "$lib/components/WithState.svelte";
    import type { MatchType } from "$lib/domlib/surround";

    import { surrounder } from "../rich-text-input";
    import { context as editorToolbarContext } from "./EditorToolbar.svelte";

    export let tagName;
    export let matcher: (element: HTMLElement | SVGElement, match: MatchType) => void;
    export let key: string;
    export let tooltip: string;
    export let keyCombination: string;
    export let exclusiveNames: string[] = [];

    const surroundElement = document.createElement(tagName);

    const format = {
        surroundElement,
        matcher,
    };

    const namedFormat = {
        key,
        name: tooltip,
        show: true,
        active: true,
    };

    const { removeFormats } = editorToolbarContext.get();
    removeFormats.update((formats) => [...formats, namedFormat]);

    async function updateStateFromActiveInput(): Promise<boolean> {
        return disabled ? false : surrounder.isSurrounded(key);
    }

    function applyAttribute(): void {
        surrounder.surround(key, exclusiveNames);
    }

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
        tooltip="{tooltip} ({getPlatformString(keyCombination)})"
        {active}
        {disabled}
        on:click={(event) => {
            applyAttribute();
            updateState(event);
            exclusiveNames.map((name) => updateStateByKey(name, event));
        }}
    >
        <slot />
    </IconButton>

    <Shortcut
        {keyCombination}
        on:action={(event) => {
            applyAttribute();
            updateState(event);
            exclusiveNames.map((name) => updateStateByKey(name, event));
        }}
    />
</WithState>
