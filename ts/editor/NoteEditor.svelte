<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Writable } from "svelte/store";
    import type { EditorFieldAPI } from "./EditorField.svelte";

    export interface NoteEditorAPI {
        readonly currentField: Writable<EditorFieldAPI | null>;
        readonly fields: EditorFieldAPI[];
    }
</script>

<script lang="ts">
    import MultiRootEditor from "./MultiRootEditor.svelte";
    import TagEditor from "./TagEditor.svelte";
    import EditorField from "./EditorField.svelte";

    import type { AdapterData } from "./adapter-types";
    import { createEventDispatcher } from "svelte";
    import { writable } from "svelte/store";
    import { setContext, noteEditorKey } from "./context";

    export let data: AdapterData;
    export let size: number;
    export let wrap: boolean;

    let className: string = "";
    export { className as class };

    const currentField = writable(null);
    let fields: EditorField[] = [];

    $: fields = fields.filter(Boolean);
    $: fieldApis = fields.map((field) => field.api);

    export const api = setContext(
        noteEditorKey,
        Object.create(
            {
                currentField,
            },
            {
                fields: { get: () => fieldApis },
            }
        )
    );

    const dispatch = createEventDispatcher();
</script>

<div class="note-editor {className}">
    <MultiRootEditor>
        <slot name="widgets" slot="widgets" />

        {#each data.fieldsData as field, index}
            <EditorField
                slot="fields"
                {field}
                bind:this={fields[index]}
                on:focusin={() => {
                    $currentField = fields[index].api;
                }}
                on:focusout={() => {
                    $currentField = null;
                }}
                on:editinginput={({ detail }) =>
                    dispatch("fieldupdate", { content: detail, index })}
            >
                <slot name="field-state" slot="field-state" {index} />
                <slot name="editing-inputs" slot="editing-inputs" {index} />
            </EditorField>
        {/each}
    </MultiRootEditor>

    <TagEditor {size} {wrap} tags={data.tags} on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;

        :global(.multi-root-editor) {
            flex-grow: 1;
        }
    }
</style>
