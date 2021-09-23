<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Extensible } from "lib/types";

    export interface EditorFieldAPI extends Extensible {
        direction: "ltr" | "rtl";
    }
</script>

<script lang="ts">
    import { setContext, getContext, onDestroy } from "svelte";
    import type { Writable } from "svelte/store";
    import { writable } from "svelte/store";
    import {
        directionKey,
        editorFieldKey,
        currentFieldKey,
        fieldsKey,
    } from "lib/context-keys";
    import type { FieldsRegisterAPI } from "./MultiRootEditor.svelte";

    export let direction: "ltr" | "rtl";

    const directionStore = writable();
    setContext(directionKey, directionStore);

    $: $directionStore = direction;

    const editorFieldAPI: EditorFieldAPI = Object.defineProperties(
        {},
        {
            direction: {
                get: () => $directionStore,
            },
        }
    );

    setContext(editorFieldKey, editorFieldAPI);

    const fields = getContext<FieldsRegisterAPI>(fieldsKey);
    const index = fields.register(editorFieldAPI) - 1;
    const currentField = getContext<Writable<EditorFieldAPI | null>>(currentFieldKey);

    onDestroy(() => fields.deregister(index));
</script>

<div
    class="editor-field"
    on:focusin={() => ($currentField = editorFieldAPI)}
    on:focusout={() => ($currentField = null)}
>
    <slot />
</div>

<style lang="scss">
    .editor-field {
        margin: 3px;

        --border-color: var(--border);

        border-radius: 5px;
        border: 1px solid var(--border-color);

        &:focus-within {
            --border-color: var(--focus-border);

            outline: none;
            box-shadow: 0 0 0 3px var(--focus-shadow);
        }
    }
</style>
