<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Row from "$lib/components/Row.svelte";
    import TagEditor from "$lib/tag-editor/TagEditor.svelte";

    export let tagsWritable;
    let globalTags: string[];
</script>

<Row --cols={1}>
    <TagEditor
        tags={tagsWritable}
        on:tagsupdate={({ detail }) => {
            globalTags = detail.tags;
            tagsWritable.set(globalTags);
        }}
        keyCombination={"Control+T"}
    />
</Row>

<style lang="scss">
    :global(.tag-editor) {
        margin: 10px 0px 10px 0px;
    }
</style>
