<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->
<script lang="ts">
    import type { CsvMetadata_MappedNotetype } from "@tslib/anki/import_export_pb";
    import { getFieldNames } from "@tslib/backend";
    import * as tr from "@tslib/ftl";

    import Spacer from "../components/Spacer.svelte";
    import type { ColumnOption } from "./lib";
    import MapperRow from "./MapperRow.svelte";

    export let columnOptions: ColumnOption[];
    export let tagsColumn: number;
    export let globalNotetype: CsvMetadata_MappedNotetype | null;

    let lastNotetypeId: bigint | undefined = -1n;
    let fieldNamesPromise: Promise<string[]>;

    $: if (globalNotetype?.id !== lastNotetypeId) {
        lastNotetypeId = globalNotetype?.id;
        fieldNamesPromise =
            globalNotetype === null
                ? Promise.resolve([])
                : getFieldNames({ ntid: globalNotetype.id }).then((list) => list.vals);
    }
</script>

{#if globalNotetype}
    {#await fieldNamesPromise then fieldNames}
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
