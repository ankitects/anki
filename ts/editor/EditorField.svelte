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
    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import { editingAreaKey, currentFieldKey, fieldsKey } from "lib/context-keys";
    import type { EditorFieldAPI, FieldsRegisterAPI } from "./MultiRootEditor.svelte";

    const editingAreaStore: Writable<EditingAreaAPI | null> = writable(null);
    setContext(editingAreaKey, editingAreaStore);

    function editingArea(): EditingAreaAPI | null {
        return $editingAreaStore;
    }

    const fields = getContext<FieldsRegisterAPI>(fieldsKey);
    const editorField = Object.defineProperty({}, "editingArea", {
        get: editingArea,
    });

    const index = fields.register(editorField) - 1;
    const currentField = getContext<Writable<EditorFieldAPI | null>>(currentFieldKey);

    onDestroy(() => fields.deregister(index));
</script>

<div
    class="editor-field"
    on:focusin={() => ($currentField = editorField)}
    on:focusout={() => ($currentField = null)}
>
    <slot {index} />
</div>

<style lang="scss">
    .editor-field {
        margin: 3px;

        border-radius: 5px;
        border: 1px solid var(--border-color);

        --border-color: var(--border);

        &:focus-within {
            box-shadow: 0 0 0 3px var(--focus-shadow);

            --border-color: var(--focus-border);
        }
    }
</style>
