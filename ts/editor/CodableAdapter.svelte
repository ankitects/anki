<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="typescript">
    import Codable from "./Codable.svelte";

    import type { Writable } from "svelte/store";
    import type { EditingInputAPI } from "./EditingArea.svelte";
    import { getContext, createEventDispatcher } from "svelte";
    import { activeInputKey, focusInCodableKey } from "lib/context-keys";

    export let content: string;

    let codable: Codable;

    const activeInput = getContext<Writable<EditingInputAPI | null>>(activeInputKey);

    $: if (codable && $activeInput !== codable.api) {
        codable.api.fieldHTML = content;
    }

    const parseStyle = "<style>anki-mathjax { white-space: pre; }</style>";

    const dispatch = createEventDispatcher();
    const focusInCodable = getContext<Writable<boolean>>(focusInCodableKey);
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
