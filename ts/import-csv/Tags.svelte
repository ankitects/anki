<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import { writable } from "svelte/store";

    import Col from "../components/Col.svelte";
    import Row from "../components/Row.svelte";
    import TagEditor from "../editor/tag-editor/TagEditor.svelte";
    import * as tr from "../lib/ftl";

    export let globalTags: string[];

    const globalTagsWritable = writable<string[]>(globalTags);
</script>

<Row --cols={2}>
    <Col>{tr.importingGlobalTags()}</Col>
    <Col>
        <TagEditor
            tags={globalTagsWritable}
            on:tagsupdate={({ detail }) => (globalTags = detail.tags)}
        /></Col
    >
</Row>
