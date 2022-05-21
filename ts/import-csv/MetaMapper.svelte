<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import * as tr from "../lib/ftl";
    import MapperRow from "./MapperRow.svelte";

    export let columnOptions: string[];
    export let tagsColumn: number;
</script>

<MapperRow
    label={tr.editingTags()}
    options={columnOptions}
    bind:value={tagsColumn}
    baseValue={-1}
/>
