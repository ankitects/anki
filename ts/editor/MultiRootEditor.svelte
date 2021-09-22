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
    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import type { FieldData } from "./adapter-types";
    import { fieldsKey, currentFieldKey, focusInCodableKey } from "lib/context-keys";

    export let fields: FieldData[];
    export let focusTo: number;

    let className: string = "";
    export { className as class };

    setContext(focusInCodableKey, writable(false));

    const fieldsList: EditorFieldAPI[] = [];

    function register(object: EditorFieldAPI): number {
        return fieldsList.push(object);
    }

    function deregister(index: number): void {
        delete fieldsList[index];
    }

    setContext(fieldsKey, { register, deregister } as FieldsRegisterAPI);

    const currentField: Writable<EditorFieldAPI | null> = writable(null);
    setContext(currentFieldKey, currentField);

    const fieldsAPI = Object.defineProperties(
        {},
        {
            fields: {
                value: fieldsList,
            },
            currentField: {
                get: () => $currentField,
            },
        }
    );

    console.log("fieldsAPI", fieldsAPI);
</script>

<slot name="toolbar" />

<main class="fields-editor {className}">
    {#each fields as field, index}
        <slot
            name="field"
            {field}
            focusOnMount={index === focusTo}
            fieldName={field.fieldName}
            content={field.fieldContent}
            fontName={field.fontName}
            fontSize={field.fontSize}
            direction={field.rtl ? "rtl" : "ltr"}
        />
    {/each}
</main>

<style lang="scss">
    .fields-editor {
        display: flex;
        flex-direction: column;

        overflow-x: hidden;
        padding: 3px 0;
    }
</style>
