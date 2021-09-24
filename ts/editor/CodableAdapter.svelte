<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Codable from "./Codable.svelte";

    import type { Writable } from "svelte/store";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import { getContext, createEventDispatcher, onMount, onDestroy } from "svelte";
    import {
        focusInCodableKey,
        activeInputKey,
        editingInputsKey,
    } from "lib/context-keys";
    import type { DecoratedElement } from "editable/decorated";

    export let content: string;
    export let decoratedComponents: DecoratedElement[];

    let codable: Codable;

    const focusInCodable = getContext<Writable<boolean>>(focusInCodableKey);
    const activeInput = getContext<Writable<EditingInputAPI | null>>(activeInputKey);

    $: if (codable && $activeInput !== codable.api) {
        codable.api.fieldHTML = content;
    }

    // TODO Expose this somehow
    const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

    const dispatch = createEventDispatcher();

    const editingInputs = getContext<EditingInputAPI[]>(editingInputsKey);
    let editingInputIndex: number;

    onMount(() => (editingInputIndex = editingInputs.push(codable.api) - 1));
    onDestroy(() => editingInputs.splice(editingInputIndex, 1));
</script>

<Codable
    bind:this={codable}
    {parseStyle}
    on:codablefocus={() => {
        dispatch("editingfocus");
        $focusInCodable = true;
        $activeInput = codable.api;
    }}
    on:codableinput={() => dispatch("editinginput")}
    on:codableblur={() => {
        dispatch("editingblur");
        $focusInCodable = false;
        $activeInput = null;
    }}
/>
