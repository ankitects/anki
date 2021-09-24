<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    export interface MultiRootFieldAPI {}

    export interface FieldsRegisterAPI {
        register(editorField: MultiRootFieldAPI): number;
        deregister(index: number): void;
    }

    export interface MultiRootEditorAPI {
        fields: MultiRootFieldAPI[];
        currentField: MultiRootFieldAPI | null;
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
        noteEditorKey,
    } from "lib/context-keys";

    let className: string = "";
    export { className as class };

    setContext(focusInCodableKey, writable(false));

    const editorFields: MultiRootFieldAPI[] = [];

    function register(object: MultiRootFieldAPI): number {
        return editorFields.push(object);
    }

    function deregister(index: number): void {
        delete editorFields[index];
    }

    setContext(fieldsKey, { register, deregister } as FieldsRegisterAPI);

    const currentField: Writable<MultiRootFieldAPI | null> = writable(null);

    setContext(currentFieldKey, currentField);

    const api: MultiRootEditorAPI = Object.defineProperties(
        {},
        {
            fields: {
                value: editorFields,
            },
            currentField: {
                get: () => $currentField,
            },
        }
    );

    setContext(multiRootEditorKey, api);

    const noteEditorAPI = getContext(noteEditorKey);
    Object.defineProperty(noteEditorAPI, "multiRootEditor", { value: api });
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
