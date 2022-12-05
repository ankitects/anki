<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "@tslib/ftl";
    import { writable } from "svelte/store";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import TagEditor from "../tag-editor/TagEditor.svelte";

    export let globalTags: string[];
    export let updatedTags: string[];

    const globalTagsWritable = writable<string[]>(globalTags);
    const updatedTagsWritable = writable<string[]>(updatedTags);
</script>

<Row --cols={2}>
    <Col>{tr.importingTagAllNotes()}</Col>
    <Col>
        <TagEditor
            tags={globalTagsWritable}
            on:tagsupdate={({ detail }) => (globalTags = detail.tags)}
            keyCombination={"Control+T"}
        /></Col
    >
</Row>
<Row --cols={2}>
    <Col>{tr.importingTagUpdatedNotes()}</Col>
    <Col>
        <TagEditor
            tags={updatedTagsWritable}
            on:tagsupdate={({ detail }) => (updatedTags = detail.tags)}
        /></Col
    >
</Row>
