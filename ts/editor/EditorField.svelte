<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { ActiveInputAPI } from "./EditingArea.svelte";

    export interface EditingAreaAPI {
        readonly activeInput: ActiveInputAPI | null;
        toggleCodable(): void;
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
    import type { EditorFieldAPI, FieldsRegisterAPI } from "./MultiRootEditor.svelte";

    export let direction: "ltr" | "rtl";

    const directionStore = writable();
    setContext(directionKey, directionStore);

    $: $directionStore = direction;

    const editingAreaAPI: Partial<EditingAreaAPI> = {};
    const editorField = Object.defineProperties(
        {},
        {
            editingArea: {
                get: () => editingAreaAPI,
            },
            direction: {
                get: () => $directionStore,
            },
        }
    );

    setContext(editorFieldKey, editorField);

    const fields = getContext<FieldsRegisterAPI>(fieldsKey);
    const index = fields.register(editorField) - 1;
    const currentField = getContext<Writable<EditorFieldAPI | null>>(currentFieldKey);

    onDestroy(() => fields.deregister(index));
</script>

<div
    class="editor-field"
    on:focusin={() => ($currentField = editorField)}
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

            box-shadow: 0 0 0 3px var(--focus-shadow);
        }
    }
</style>
