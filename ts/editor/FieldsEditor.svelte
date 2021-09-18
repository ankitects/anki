<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditorField from "./EditorField.svelte";

    import LabelContainer from "./LabelContainer.svelte";
    import LabelName from "./LabelName.svelte";
    import FieldState from "./FieldState.svelte";

    import EditingArea from "./EditingArea.svelte";
    import ImageHandle from "./ImageHandle.svelte";
    import MathjaxHandle from "./ImageHandle.svelte";
    import EditableContainer from "editable/EditableContainer.svelte";
    import Codable from "./Codable.svelte";

    export let data = [1, 2, 3, 4];
</script>

<main class="fields-editor">
    {#each data as _foo}
        <EditorField>
            <LabelContainer>
                <LabelName />
                <FieldState />
            </LabelContainer>
            <EditingArea>
                {#if true}
                    <EditableContainer />

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
        display: grid;
        flex-grow: 1;
        overflow-x: hidden;
        margin: 3px 0;
    }
</style>
