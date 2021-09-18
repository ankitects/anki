<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditorToolbar from "./EditorToolbar.svelte";
    import FieldsEditor from "./FieldsEditor.svelte";
    import TagEditor from "./TagEditor.svelte";

    let className: string = "";
    export { className as class };
</script>

<div class="note-editor {className}">
    <EditorToolbar size={2.0} wrap />
    <FieldsEditor />
    <TagEditor />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
