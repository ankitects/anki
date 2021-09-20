<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { FieldData } from "./adapter-types";

    import EditorField from "./EditorField.svelte";

    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";

    import EditingArea from "./EditingArea.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./ImageHandle.svelte";
    import EditableContainer from "editable/EditableContainer.svelte";
    import Codable from "./Codable.svelte";

    export let fields: FieldData[];
    export let focusTo: number;

    let className: string = "";
    export { className as class };
</script>

<main class="fields-editor {className}">
    {#each fields as field, index}
        <EditorField>
            <LabelContainer>
                <LabelName>{field.fieldName}</LabelName>
                <FieldState />
            </LabelContainer>
            <EditingArea>
                {#if true}
                    <EditableContainer
                        content={field.fieldContent}
                        focusOnMount={index === focusTo}
                    />

                    <div class="editable-handles">
                        <ImageHandle
                            activeImage={null}
                            container={document.body}
                            isRtl={false}
                        />
                        <MathjaxHandle
                            activeImage={null}
                            container={document.body}
                            isRtl={false}
                        />
                        <!-- TODO extensible -->
                    </div>
                {:else}
                    <Codable />
                {/if}
            </EditingArea>
        </EditorField>
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
