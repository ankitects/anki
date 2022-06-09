<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import Spacer from "../components/Spacer.svelte";
    import * as tr from "../lib/ftl";
    import type { ImportExport } from "../lib/proto";
    import type { ColumnOption } from "./lib";
    import { getNotetypeFields } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let columnOptions: ColumnOption[];
    export let tagsColumn: number;
    export let globalNotetype: ImportExport.CsvMetadata.MappedNotetype | null;
</script>

{#if globalNotetype}
    {#await getNotetypeFields(globalNotetype.id) then fieldNames}
        {#each fieldNames as label, idx}
            <!-- first index is treated specially, because it must be assigned some column -->
            <MapperRow
                {label}
                columnOptions={idx === 0 ? columnOptions.slice(1) : columnOptions}
                bind:value={globalNotetype.fieldColumns[idx]}
            />
        {/each}
    {/await}
    <Spacer --height="1.5rem" />
{/if}
<MapperRow label={tr.editingTags()} {columnOptions} bind:value={tagsColumn} />
