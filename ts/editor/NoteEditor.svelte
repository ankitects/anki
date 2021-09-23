<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { Extensible } from "lib/types";
    import type { MultiRootEditorAPI } from "./MultiRootEditor.svelte";
    export interface NoteEditorAPI extends Extensible {
        readonly multiRootEditor: MultiRootEditorAPI,
    }
</script>

<script lang="ts">
    import MultiRootEditor from "./MultiRootEditor.svelte";
    import TagEditor from "./TagEditor.svelte";

    import EditorField from "./EditorField.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";
    import EditingArea from "./EditingArea.svelte";

    import type { SvelteComponent } from "svelte";
    import type { AdapterData } from "./adapter-types";
    import { writable } from "svelte/store";
    import { setContext, createEventDispatcher } from "svelte";
    import { fieldFocusedKey, noteEditorKey } from "lib/context-keys";

    export let data: AdapterData;
    export let editingInputs: typeof SvelteComponent[];
    export let size: number;
    export let wrap: boolean;

    let className: string = "";
    export { className as class };

    const dispatch = createEventDispatcher();

    const fieldFocused = writable(false);
    setContext(fieldFocusedKey, fieldFocused);

    // TODO end of downwards tree for now
    export const api: Partial<NoteEditorAPI> = {};

    setContext(noteEditorKey, api);
</script>

<div class="note-editor {className}">
    <MultiRootEditor class="flex-grow-1">
        <slot name="toolbar" slot="toolbar" />

        {#each data.fieldsData as field, index}
            <EditorField slot="field" direction={field.rtl ? "rtl" : "ltr"}>
                <EditingArea
                    {editingInputs}
                    fontFamily={field.fontName}
                    fontSize={field.fontSize}
                    content={field.fieldContent}
                    on:editingfocus={() => ($fieldFocused = true)}
                    on:editinginput={() => dispatch("fieldupdate", index)}
                    on:editingblur={() => {
                        $fieldFocused = false;
                        dispatch("fieldblur", index);
                    }}
                >
                    <LabelContainer slot="label">
                        <LabelName>{field.fieldName}</LabelName>
                        <FieldState />
                    </LabelContainer>
                </EditingArea>
            </EditorField>
        {/each}
    </MultiRootEditor>

    <TagEditor {size} {wrap} tags={data.tags} on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
