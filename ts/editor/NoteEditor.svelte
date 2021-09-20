<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditorToolbar from "./EditorToolbar.svelte";
    import FieldsArea from "./FieldsArea.svelte";
    import TagEditor from "./TagEditor.svelte";

    import EditorField from "./EditorField.svelte";

    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";

    import EditingArea from "./EditingArea.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./ImageHandle.svelte";
    import EditableContainer from "editable/EditableContainer.svelte";
    import Codable from "./Codable.svelte";

    import { isApplePlatform } from "lib/platform";
    import type { AdapterData } from "./adapter-types";

    export let data: AdapterData;
    export let size: number = isApplePlatform() ? 1.6 : 2.0;
    export let wrap: boolean = false;

    let resetTags: (tags: string[]) => void = () => {
        /* noop */
    };
    $: resetTags(data.tags);

    let className: string = "";
    export { className as class };
</script>

<div class="note-editor {className}">
    <EditorToolbar
        {size}
        {wrap}
        textColor={data.textColor}
        highlightColor={data.highlightColor}
    />
    <FieldsArea
        class="flex-grow-1"
        fields={data.fieldsData}
        focusTo={data.focusTo}
        let:focusOnMount
        let:fieldName
        let:content
    >
        <EditorField>
            <LabelContainer>
                <LabelName>{fieldName}</LabelName>
                <FieldState />
            </LabelContainer>
            <EditingArea let:editingArea>
                {#if true}
                    <EditableContainer {content} {focusOnMount} />

                    <div class="editable-handles">
                        <!--<ImageHandle
                            activeImage={null}
                            container={null}
                            isRtl={field.rtl}
                        />
                        <MathjaxHandle
                            activeImage={null}
                            container={null}
                            isRtl={field.rtl}
                            />-->
                        <!-- TODO extensible -->
                    </div>
                {:else}
                    <Codable />
                {/if}
            </EditingArea>
        </EditorField>
    </FieldsArea>
    <TagEditor {size} {wrap} tags={data.tags} bind:resetTags on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
