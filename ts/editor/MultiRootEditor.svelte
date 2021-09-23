<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditingAreaAPI } from "./EditorField.svelte";

    export interface EditorFieldAPI {
        readonly editingArea: EditingAreaAPI | null;
    }

    export interface FieldsRegisterAPI {
        register(editorField: EditorFieldAPI): number;
        deregister(index: number): void;
    }
</script>

<script lang="ts">
    import { setContext, getContext } from "svelte";
    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import {
        fieldsKey,
        currentFieldKey,
        multiRootEditorKey,
        focusInCodableKey,
    } from "lib/context-keys";
    import type { MultiRootEditorAPI } from "./NoteEditor.svelte";

    let className: string = "";
    export { className as class };

    setContext(focusInCodableKey, writable(false));

    const editorFields: EditorFieldAPI[] = [];

    function register(object: EditorFieldAPI): number {
        return editorFields.push(object);
    }

    function deregister(index: number): void {
        delete editorFields[index];
    }

    setContext(fieldsKey, { register, deregister } as FieldsRegisterAPI);

    const currentField: Writable<EditorFieldAPI | null> = writable(null);

    setContext(currentFieldKey, currentField);

    const multiRootEditor =
        getContext<Writable<Partial<MultiRootEditorAPI>>>(multiRootEditorKey);
    Object.defineProperties(multiRootEditor, {
        fields: {
            value: editorFields,
        },
        currentField: {
            get: () => $currentField,
        },
    });
</script>

<slot name="toolbar" />

<main class="fields-editor {className}">
    <slot />
</main>

<style lang="scss">
    .fields-editor {
        display: flex;
        flex-direction: column;

        overflow-x: hidden;
        padding: 3px 0;
    }
</style>
