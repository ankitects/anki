<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import MultiRootEditor from "./MultiRootEditor.svelte";
    import EditorToolbar from "./EditorToolbar.svelte";
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

    import { setContext } from "svelte";
    import { writable } from "svelte/store";
    import { isApplePlatform } from "lib/platform";
    import { fieldFocusedKey, focusInCodableKey } from "lib/context-keys";
    import type { AdapterData } from "./adapter-types";

    export let data: AdapterData;
    export let size: number = isApplePlatform() ? 1.6 : 2.0;
    export let wrap: boolean = true;

    let className: string = "";
    export { className as class };

    const focusInCodable = writable(false);

    setContext(fieldFocusedKey, writable(false));
    setContext(focusInCodableKey, focusInCodable);
</script>

<div class="note-editor {className}">
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
        <EditorToolbar
            slot="toolbar"
            {size}
            {wrap}
            textColor={data.textColor}
            highlightColor={data.highlightColor}
        />

        <EditorField slot="field">
            <LabelContainer>
                <LabelName>{fieldName}</LabelName>
                <FieldState />
            </LabelContainer>
            <EditingArea let:activeInput>
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
                    <Codable />
                {/if}
            </EditingArea>
        </EditorField>
    </MultiRootEditor>

    <TagEditor {size} {wrap} tags={data.tags} on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
