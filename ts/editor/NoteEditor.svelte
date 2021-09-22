<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script context="module" lang="ts">
    import type { EditorFieldAPI } from "./MultiRootEditor.svelte";

    export interface MultiRootEditorAPI {
        readonly fields: EditorFieldAPI[];
        readonly currentField: EditorFieldAPI | null;
    }
</script>

<script lang="ts">
    import MultiRootEditor from "./MultiRootEditor.svelte";
    import DecoratedComponents from "./DecoratedComponents.svelte";
    import TagEditor from "./TagEditor.svelte";

    import EditorField from "./EditorField.svelte";
    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";
    import EditingArea from "./EditingArea.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./MathjaxHandle.svelte";
    import EditableContainer from "editable/EditableContainer.svelte";
    import Codable from "./Codable.svelte";

    import { writable } from "svelte/store";
    import type { Writable } from "svelte/store";
    import { setContext, createEventDispatcher } from "svelte";
    import { fieldFocusedKey, multiRootEditorKey } from "lib/context-keys";
    import type { AdapterData } from "./adapter-types";

    export let data: AdapterData;
    export let size: number;
    export let wrap: boolean;

    let className: string = "";
    export { className as class };

    const dispatch = createEventDispatcher();

    const fieldFocused = writable(false);
    setContext(fieldFocusedKey, fieldFocused);

    const multiRootEditor: Writable<MultiRootEditorAPI | null> = writable(null);
    setContext(multiRootEditorKey, multiRootEditor);

    // TODO end of downwards tree for now
    export const noteEditor = Object.defineProperties(
        {},
        {
            multiRootEditor: {
                get: () => $multiRootEditor,
            },
        }
    );
</script>

<div class="note-editor {className}">
    <DecoratedComponents>
        <MultiRootEditor
            class="flex-grow-1"
            fields={data.fieldsData}
            focusTo={data.focusTo}
            let:focusOnMount
            let:fieldName
            let:content
            let:fontName
            let:fontSize
            let:direction
        >
            <slot name="toolbar" slot="toolbar" />

            <svelte:fragment slot="field">
                <EditorField let:index>
                    <LabelContainer>
                        <LabelName>{fieldName}</LabelName>
                        <FieldState />
                    </LabelContainer>
                    <EditingArea
                        {content}
                        let:activeInput
                        on:focusin={() => ($fieldFocused = true)}
                        on:input={() => dispatch("fieldupdate", index)}
                        on:focusout={() => {
                            $fieldFocused = false;
                            dispatch("fieldblur", index);
                        }}
                    >
                        {#if activeInput === "editable"}
                            <EditableContainer
                                {content}
                                {focusOnMount}
                                {fontName}
                                {fontSize}
                                {direction}
                                let:imageOverlaySheet
                                let:overlayRelative={container}
                            >
                                {#await imageOverlaySheet then sheet}
                                    <ImageHandle
                                        activeImage={null}
                                        {container}
                                        isRtl={direction === "rtl"}
                                        {sheet}
                                    />
                                {/await}
                                <MathjaxHandle
                                    activeImage={null}
                                    {container}
                                    isRtl={direction === "rtl"}
                                />
                            </EditableContainer>
                        {:else if activeInput === "codable"}
                            <Codable {content} />
                        {/if}
                    </EditingArea>
                </EditorField>
            </svelte:fragment>
        </MultiRootEditor>
    </DecoratedComponents>

    <TagEditor {size} {wrap} tags={data.tags} on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
