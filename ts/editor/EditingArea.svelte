<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingAreaAPI } from "./EditorField.svelte";

    export interface ActiveInputAPI {
        readonly name: string;
        fieldHTML: string;
        focus(): void;
        moveCaretToEnd(): void;
    }
</script>

<script lang="ts">
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import { setContext, getContext } from "svelte";
    import { editingAreaKey, activeInputKey } from "lib/context-keys";

    export let content: string;

    let codableActive = false;

    const activeInput: Writable<ActiveInputAPI | null> = writable(null);

    setContext(activeInputKey, activeInput);

    function fetchContent() {
        content = $activeInput!.fieldHTML;
    }

    /* if (fieldChanged) { */
    /*     editingArea.resetHandles(); */
    /* } */

    const editingAreaAPI = getContext<Partial<EditingAreaAPI>>(editingAreaKey);
    Object.defineProperties(editingAreaAPI, {
        activeInput: {
            get: () => $activeInput,
        },
        toggleCodable: {
            value: () => (codableActive = !codableActive),
        },
    });
</script>

<div class="editing-area" on:focusin on:input={fetchContent} on:input on:focusout>
    <slot {content} />
</div>

<style>
    .editing-area {
        background: var(--frame-bg);
        border-radius: 0 0 5px 5px;

        transition: height 5s;

        /* TODO move this up one layer */
        /* &.dupe { */
        /*     // this works around the background colour persisting in copy+paste */
        /*     // (https://github.com/ankitects/anki/pull/1278) */
        /*     background-image: linear-gradient(var(--flag1-bg), var(--flag1-bg)); */
        /* } */
    }
</style>
