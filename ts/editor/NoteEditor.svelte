<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import EditorToolbar from "./EditorToolbar.svelte";
    import FieldsEditor from "./FieldsEditor.svelte";
    import TagEditor from "./TagEditor.svelte";

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
    <FieldsEditor class="flex-grow-1" fields={data.fieldsData} focusTo={data.focusTo} />
    <TagEditor {size} {wrap} tags={data.tags} bind:resetTags on:tagsupdate />
</div>

<style lang="scss">
    .note-editor {
        display: flex;
        flex-direction: column;
    }
</style>
